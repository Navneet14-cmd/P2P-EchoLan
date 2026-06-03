package com.robustians.echolan;

import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

import com.robustians.echolan.db.MessageRepository;
import com.robustians.echolan.db.SqliteMessageDao;
import com.robustians.echolan.model.*;
import com.robustians.encoding.Bip39Handler;
import com.robustians.utils.CLI;
import com.robustians.utils.NetworkSelector;
import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.PublicKey;
import com.robustians.echolan.crypto.CryptoEngine;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.reader.*;

public class App {
    private static int SERVER_PORT = 60000;

    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String END = "\u001B[0m";

    private static final int MSG_LOCAL = 0;
    private static final int MSG_REMOTE = 1;

    private static String localIp;
    private static String localIpWords;
    private static String currentPeerId;

    private static ServerSocket serverSocket;
    private static Socket clientSocket;

    private static Bip39Handler bip39Handler;

    private static List<Message> messages = new ArrayList<>();

    private static final Object PRINT_LOCK = new Object();

    private static final int MAX_MESSAGE_HISTORY = 1000;
    private static MessageRepository messageRepository;
    private static SecretKey aesKey;

    private static void safePrintln(String msg) {
        synchronized (PRINT_LOCK) {
            System.out.println(msg);
        }
    }

    private static void promptNetworkInterfaceSelection() throws IOException {
        NetworkInterface ni = NetworkSelector.chooseInterface();

        if (ni == null) {
            safePrintln("No valid network interface selected.");
            System.exit(1);
        }

        localIp = NetworkSelector.getIp(ni);

        if (localIp == null) {
            safePrintln("Selected interface does not have a valid IPv4 address.");
            System.exit(1);
        }

        localIpWords = bip39Handler.ipToWords(localIp);
    }

    private static void connectToServer(String remoteMagicAddress) throws IOException {
        clientSocket = new Socket(bip39Handler.wordsToIp(remoteMagicAddress), SERVER_PORT);
    }

    public static void main(String[] args) throws Exception {
        try {
            CLI.clear();

            bip39Handler = new Bip39Handler();
            promptNetworkInterfaceSelection();

            serverSocket = new ServerSocket(SERVER_PORT);

            messageRepository = new MessageRepository(new SqliteMessageDao(), MAX_MESSAGE_HISTORY);
            messageRepository.init();

            CLI.clear();

            safePrintln("Welcome to EchoLAN!\n");
            safePrintln("IP Address: " + localIp);
            safePrintln("Magic Address: " + localIpWords);

            AtomicBoolean connected = new AtomicBoolean(false);
            AtomicBoolean done = new AtomicBoolean(false);
            final String[] userInput = new String[1];

            Terminal terminal = TerminalBuilder.builder().system(true).build();
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true) // fixes ! issue
                    .option(LineReader.Option.AUTO_REMOVE_SLASH, false) // keep slashes
                    .option(LineReader.Option.INSERT_TAB, true) // optional
                    .build();

            // 🔹 Input thread
            Thread inputThread = new Thread(() -> {
                try {
                    String line = reader.readLine("\nRemote address: ");
                    if (!done.get()) {
                        userInput[0] = line;
                        done.set(true);
                    }
                } catch (UserInterruptException | EndOfFileException e) {
                    cleanupAndExit();
                } catch (Exception ignored) {
                }
            });

            // 🔹 Accept thread
            Thread acceptThread = new Thread(() -> {
                try {
                    Socket socket = serverSocket.accept();
                    if (!done.get()) {
                        clientSocket = socket;
                        connected.set(true);
                        done.set(true);

                        safePrintln("\nClient connected: " +
                                socket.getInetAddress().getHostAddress());
                    } else {
                        socket.close();
                    }
                } catch (IOException ignored) {
                }
            });

            inputThread.start();
            acceptThread.start();

            while (!done.get()) {
                Thread.sleep(50);
            }

            if (!connected.get()) {
                if (userInput[0].equals(localIpWords)) {
                    safePrintln(RED + "\nCannot connect to yourself!" + END);
                    System.exit(1);
                }

                try {
                    connectToServer(userInput[0]);
                } catch (IOException e) {
                    safePrintln(RED + "\nFailed to connect!" + END);
                    System.exit(1);
                } catch (IllegalArgumentException e) {
                    safePrintln(RED + "\nInvalid magic address: " + userInput[0] + END);
                    System.exit(1);
                }
            }

            LineReader chatReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                    .option(LineReader.Option.AUTO_REMOVE_SLASH, false)
                    .option(LineReader.Option.INSERT_TAB, true)
                    .build();

            boolean isServer = connected.get();
            initiateChatSession(chatReader, isServer);

        } catch (UserInterruptException | EndOfFileException e) {
            cleanupAndExit();
        }
    }

    private static void initiateChatSession(LineReader reader, boolean isServer) throws IOException {
        BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream()));
        PrintWriter out = new PrintWriter(
                clientSocket.getOutputStream(), true);

        String remoteHostAddress = clientSocket.getInetAddress().getHostAddress();
        currentPeerId = remoteHostAddress;

        // Perform secure handshake
        try {
            safePrintln("\n" + YELLOW + "[Security] Performing secure handshake..." + END);

            // 1. Generate local RSA key pair
            KeyPair localRsaKeyPair = CryptoEngine.generateRsaKeyPair();
            String localPubKeyB64 = CryptoEngine.encodePublicKey(localRsaKeyPair.getPublic());

            // 2. Transmit local public key
            out.println("INIT_PUBKEY:" + localPubKeyB64);

            // 3. Receive peer's public key
            String peerPubKeyLine = in.readLine();
            if (peerPubKeyLine == null || !peerPubKeyLine.startsWith("INIT_PUBKEY:")) {
                throw new IOException("Handshake failed: Did not receive valid remote public key");
            }
            String peerPubKeyB64 = peerPubKeyLine.substring(12);
            PublicKey peerPublicKey = CryptoEngine.decodePublicKey(peerPubKeyB64);

            // 4. Exchange session key
            if (!isServer) {
                // Client generates, encrypts and transmits AES key
                aesKey = CryptoEngine.generateAesKey();
                String encryptedAesKey = CryptoEngine.encryptAesKey(aesKey, peerPublicKey);
                out.println("INIT_SESSIONKEY:" + encryptedAesKey);
            } else {
                // Server waits for the encrypted AES key and decrypts it
                String sessionKeyLine = in.readLine();
                if (sessionKeyLine == null || !sessionKeyLine.startsWith("INIT_SESSIONKEY:")) {
                    throw new IOException("Handshake failed: Did not receive valid encrypted session key");
                }
                String encryptedAesKey = sessionKeyLine.substring(16);
                aesKey = CryptoEngine.decryptAesKey(encryptedAesKey, localRsaKeyPair.getPrivate());
            }

            safePrintln(GREEN + "[Security] Secure session established using AES-256-GCM." + END);
            Thread.sleep(1000); // Let the user see the success message briefly
        } catch (Exception e) {
            safePrintln(RED + "\n[Security] Handshake failed: " + e.getMessage() + END);
            cleanupAndExit();
            return;
        }

        // Reload only this peer's history
        if (messageRepository != null) {
            synchronized (messages) {
                messages.clear();
                messages.addAll(messageRepository.getRecentMessages(currentPeerId));
            }
        }

        // 🔹 Incoming thread
        new Thread(() -> {
            try {
                String rawLine;

                while ((rawLine = in.readLine()) != null) {

                    // 🔥 Handle remote exit (plaintext check as fallback)
                    if (rawLine.equalsIgnoreCase(".exit")) {
                        System.out.println(RED + "Peer disconnected!" + END);
                        cleanupAndExit();
                    }

                    String decryptedMessage;
                    if (rawLine.startsWith("SECURE_MSG:")) {
                        try {
                            decryptedMessage = CryptoEngine.decryptMessage(rawLine.substring(11), aesKey);
                        } catch (Exception e) {
                            System.out.println(RED + "Failed to decrypt incoming message!" + END);
                            continue;
                        }
                    } else {
                        // Ignore unencrypted traffic during secure session
                        continue;
                    }

                    // Check if decrypted message is remote exit command
                    if (decryptedMessage.equalsIgnoreCase(".exit")) {
                        System.out.println(RED + "Peer disconnected!" + END);
                        cleanupAndExit();
                    }

                    if (decryptedMessage.startsWith(".image ")) {
                        String base64Image = decryptedMessage.substring(7).trim();
                        showImage(base64Image);

                        synchronized (messages) {
                            Message msgObj = new Message(MSG_REMOTE, "Received an image", currentPeerId);
                            messages.add(msgObj);
                            if (messageRepository != null) {
                                messageRepository.save(msgObj);
                            }
                        }

                        redraw(remoteHostAddress);
                        continue;
                    }

                    synchronized (messages) {
                        Message msgObj = new Message(MSG_REMOTE, decryptedMessage, currentPeerId);
                        messages.add(msgObj);
                        if (messageRepository != null) {
                            messageRepository.save(msgObj);
                        }
                    }

                    redraw(remoteHostAddress);
                }
            } catch (IOException ignored) {
                System.out.println(RED + "Connection lost!" + END);
                cleanupAndExit();
            }
        }).start();

        // 🔹 Sending loop
        while (true) {
            redraw(remoteHostAddress);

            String message;
            try {
                message = reader.readLine();
            } catch (UserInterruptException | EndOfFileException e) {
                cleanupAndExit();
                return;
            }

            if (message == null)
                continue;

            message = message.trim();

            if (message.isEmpty())
                continue;

            // 🔥 LOCAL EXIT
            if (message.equalsIgnoreCase(".exit")) {
                try {
                    String encryptedExit = CryptoEngine.encryptMessage(".exit", aesKey);
                    out.println("SECURE_MSG:" + encryptedExit);
                } catch (Exception ignored) {
                }
                out.println(".exit"); // Send plaintext exit as fallback/extra signal
                cleanupAndExit();
                return;
            }

            if (message.startsWith(".image ")) {
                String imagePath = message.substring(7).trim();
                if ((imagePath.startsWith("\"") && imagePath.endsWith("\""))
                        || (imagePath.startsWith("'") && imagePath.endsWith("'"))) {
                    imagePath = imagePath.substring(1, imagePath.length() - 1).trim();
                }
                File imgFile = new File(imagePath);

                if (!imgFile.exists() || !imgFile.isFile()) {
                    messages.add(new Message(MSG_LOCAL, RED + "Invalid image path!" + END));
                    continue;
                }

                try {
                    // 🔥 Encode image and send as message
                    byte[] imageBytes = Files.readAllBytes(imgFile.toPath());
                    String base64Image = Base64.getEncoder().encodeToString(imageBytes);

                    synchronized (messages) {
                        Message msgObj = new Message(MSG_LOCAL, "Sent an image", currentPeerId);
                        messages.add(msgObj);
                        if (messageRepository != null) {
                            messageRepository.save(msgObj);
                        }
                    }

                    String imageMessage = ".image " + base64Image;
                    String encryptedImageMessage = CryptoEngine.encryptMessage(imageMessage, aesKey);
                    out.println("SECURE_MSG:" + encryptedImageMessage);
                    continue;
                } catch (Exception e) {
                    messages.add(new Message(MSG_LOCAL, RED + "Failed to encrypt/send image!" + END));
                    continue;
                }
            }

            synchronized (messages) {
                Message msgObj = new Message(MSG_LOCAL, message, currentPeerId);
                messages.add(msgObj);
                if (messageRepository != null) {
                    messageRepository.save(msgObj);
                }
            }

            try {
                String encryptedMsg = CryptoEngine.encryptMessage(message, aesKey);
                out.println("SECURE_MSG:" + encryptedMsg);
            } catch (Exception e) {
                messages.add(new Message(MSG_LOCAL, RED + "Failed to encrypt message!" + END));
            }
            redraw(remoteHostAddress);
        }
    }

    private static void showImage(String base64) {
        try {
            // 1. Decode Base64
            byte[] imageBytes = Base64.getDecoder().decode(base64);

            // 2. Convert to BufferedImage
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));

            if (image == null) {
                System.out.println("Invalid image data!");
                return;
            }

            // 3. Show in window
            JFrame frame = new JFrame("Received Image");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            JLabel label = new JLabel(new ImageIcon(image));
            frame.getContentPane().add(label, BorderLayout.CENTER);

            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void redraw(String remoteHostAddress) {
        synchronized (PRINT_LOCK) {
            CLI.clear();

            System.out.println(GREEN + "Connected to: " + remoteHostAddress + END);
            System.out.println(YELLOW + "\n--------------------------------------------------" + END);

            synchronized (messages) {
                if (messages.isEmpty()) {
                    System.out.println("No messages yet. Start chatting!");
                }

                for (Message msg : messages) {
                    if (msg.getType() == MSG_LOCAL) {
                        System.out.println(YELLOW + "You: " + END + msg.getContent());
                    } else {
                        System.out.println(YELLOW + "[" + remoteHostAddress + "]: " + END + msg.getContent());
                    }
                }
            }

            System.out.println(YELLOW + "--------------------------------------------------\n" + END);
            System.out.print("Message: ");
        }
    }

    private static void cleanupAndExit() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }

        if (messageRepository != null) {
            messageRepository.close();
        }

        System.exit(0);
    }
}