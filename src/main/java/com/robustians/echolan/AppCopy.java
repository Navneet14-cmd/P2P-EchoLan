// package com.robustians.echolan;

// import java.io.*;
// import java.net.*;
// import java.util.*;
// import java.util.concurrent.atomic.AtomicBoolean;

// import com.robustians.echolan.model.*;
// import com.robustians.encoding.Bip39Handler;
// import com.robustians.utils.CLI;
// import com.robustians.utils.NetworkSelector;

// // 🔥 JLINE IMPORTS
// import org.jline.terminal.Terminal;
// import org.jline.terminal.TerminalBuilder;
// import org.jline.reader.EndOfFileException;
// import org.jline.reader.LineReader;
// import org.jline.reader.LineReaderBuilder;
// import org.jline.reader.UserInterruptException;

// public class App {
//     private static int SERVER_PORT = 60000;

//     private static final String BLUE = "\u001B[34m";
//     private static final String GREEN = "\u001B[32m";
//     private static final String RED = "\u001B[31m";
//     private static final String YELLOW = "\u001B[33m";
//     private static final String END = "\u001B[0m";

//     private static final int MSG_LOCAL = 0;
//     private static final int MSG_REMOTE = 1;

//     private static String localIp;
//     private static String localIpWords;

//     private static ServerSocket serverSocket;
//     private static Socket clientSocket;

//     private static Bip39Handler bip39Handler;

//     private static List<Message> messages = new ArrayList<>();

//     private static final Object PRINT_LOCK = new Object();

//     private static void safePrintln(String msg) {
//         synchronized (PRINT_LOCK) {
//             System.out.println(msg);
//         }
//     }

//     private static void promptNetworkInterfaceSelection() throws IOException {
//         NetworkInterface ni = NetworkSelector.chooseInterface();

//         if (ni == null) {
//             safePrintln("No valid network interface selected.");
//             System.exit(1);
//         }

//         localIp = NetworkSelector.getIp(ni);

//         if (localIp == null) {
//             safePrintln("Selected interface does not have a valid IPv4 address.");
//             System.exit(1);
//         }

//         localIpWords = bip39Handler.ipToWords(localIp);
//     }

//     private static void connectToServer(String remoteMagicAddress) throws IOException, IllegalArgumentException {
//         clientSocket = new Socket(bip39Handler.wordsToIp(remoteMagicAddress), SERVER_PORT);
//     }

//     public static void main(String[] args) throws Exception {
//         try {
//             CLI.clear();

//             bip39Handler = new Bip39Handler();
//             promptNetworkInterfaceSelection();

//             serverSocket = new ServerSocket(SERVER_PORT);

//             CLI.clear();

//             safePrintln("Welcome to EchoLAN!\n");
//             safePrintln("IP Address: " + localIp);
//             safePrintln("Magic Address: " + localIpWords);

//             AtomicBoolean connected = new AtomicBoolean(false);
//             AtomicBoolean done = new AtomicBoolean(false);
//             final String[] userInput = new String[1];

//             // 🔥 Setup JLine
//             Terminal terminal = TerminalBuilder.builder().system(true).build();
//             LineReader reader = LineReaderBuilder.builder()
//                     .terminal(terminal)
//                     .build();

//             // 🔹 Input thread
//             Thread inputThread = new Thread(() -> {
//                 try {
//                     String line = reader.readLine("\nRemote address: ");
//                     if (!done.get()) {
//                         userInput[0] = line;
//                         done.set(true);
//                     }
//                 } catch (Exception ignored) {
//                 }
//             });

//             // 🔹 Accept thread
//             Thread acceptThread = new Thread(() -> {
//                 try {
//                     Socket socket = serverSocket.accept();
//                     if (!done.get()) {
//                         clientSocket = socket;
//                         connected.set(true);
//                         done.set(true);

//                         // 🔥 Interrupt input instantly
//                         terminal.raise(Terminal.Signal.INT);

//                         safePrintln("\nClient connected: " +
//                                 socket.getInetAddress().getHostAddress());
//                     } else {
//                         socket.close();
//                     }
//                 } catch (IOException ignored) {
//                 }
//             });

//             inputThread.start();
//             acceptThread.start();

//             while (!done.get()) {
//                 Thread.sleep(50);
//             }

//             if (!connected.get()) {
//                 if (userInput[0].equals(localIpWords)) {
//                     safePrintln(RED + "\nCannot connect to yourself!" + END);
//                     System.exit(1);
//                 }

//                 try {
//                     connectToServer(userInput[0]);
//                 } catch (IOException e) {
//                     safePrintln(RED + "\nFailed to connect!" + END);
//                     System.exit(1);
//                 } catch (IllegalArgumentException e) {
//                     safePrintln(RED + "\nInvalid magic address: " + userInput[0] + END);
//                     System.exit(1); 
//                 }
//             }

//             initiateChatSession(reader);
//         } catch (UserInterruptException e) {
//             System.exit(0);
//         } catch (EndOfFileException s) {
//             System.exit(0);
//         }
//     }

//     private static void initiateChatSession(LineReader reader) throws IOException {
//         BufferedReader in = new BufferedReader(
//                 new InputStreamReader(clientSocket.getInputStream()));
//         PrintWriter out = new PrintWriter(
//                 clientSocket.getOutputStream(), true);

//         String remoteHostAddress = clientSocket.getInetAddress().getHostAddress();

//         // Incoming messages thread
//         new Thread(() -> {
//             try {
//                 String message;

//                 while ((message = in.readLine()) != null) {
//                     synchronized (messages) {
//                         messages.add(new Message(MSG_REMOTE, message));
//                     }
//                     redraw(remoteHostAddress);
//                 }
//             } catch (IOException ignored) {
//                 System.out.println(RED + "Connection lost!" + END);
//                 System.exit(0);
//             }
//         }).start();

//         // Sending messages thread
//         while (true) {
//             redraw(remoteHostAddress);

//             String message = reader.readLine();
//             if (message == null || message.trim().isEmpty()) {
//                 continue;
//             }

//             synchronized (messages) {
//                 messages.add(new Message(MSG_LOCAL, message));
//             }

//             out.println(message);
//             redraw(remoteHostAddress);
//         }
//     }

//     private static void redraw(String remoteHostAddress) {
//         synchronized (PRINT_LOCK) {
//             CLI.clear();

//             System.out.println(GREEN + "Connected to: " + remoteHostAddress + END);
//             System.out.println(YELLOW + "\n--------------------------------------------------" + END);

//             synchronized (messages) {
//                 if (messages.isEmpty()) {
//                     System.out.println("No messages yet. Start chatting!");
//                 }

//                 for (Message msg : messages) {
//                     if (msg.getType() == MSG_LOCAL) {
//                         System.out.println(YELLOW + "You: " + END + msg.getContent());
//                     } else {
//                         System.out.println(YELLOW + "[" + remoteHostAddress + "]: " + END + msg.getContent());
//                     }
//                 }
//             }

//             System.out.println(YELLOW + "--------------------------------------------------\n" + END);
//             System.out.print("Message: ");
//         }
//     }
// }