package com.robustians.echolan.crypto;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoEngine {
    private static final int RSA_KEY_SIZE = 2048;
    private static final String RSA_ALGORITHM = "RSA";
    private static final String RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding";
    
    private static final int AES_KEY_SIZE = 256;
    private static final String AES_ALGORITHM = "AES";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128; // bits

    /**
     * Generates a new ephemeral 2048-bit RSA key pair.
     */
    public static KeyPair generateRsaKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(RSA_ALGORITHM);
        kpg.initialize(RSA_KEY_SIZE);
        return kpg.generateKeyPair();
    }

    /**
     * Converts a PublicKey to its Base64 X.509 representation.
     */
    public static String encodePublicKey(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * Reconstructs a PublicKey from its Base64 X.509 representation.
     */
    public static PublicKey decodePublicKey(String base64Key) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance(RSA_ALGORITHM);
        return kf.generatePublic(spec);
    }

    /**
     * Generates a random AES-256 session key.
     */
    public static SecretKey generateAesKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance(AES_ALGORITHM);
        keyGen.init(AES_KEY_SIZE);
        return keyGen.generateKey();
    }

    /**
     * Encrypts the AES key bytes using the peer's RSA Public Key.
     */
    public static String encryptAesKey(SecretKey aesKey, PublicKey peerPublicKey) throws Exception {
        Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, peerPublicKey);
        byte[] encryptedBytes = cipher.doFinal(aesKey.getEncoded());
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    /**
     * Decrypts the AES key bytes using the local RSA Private Key.
     */
    public static SecretKey decryptAesKey(String base64EncryptedAesKey, PrivateKey localPrivateKey) throws Exception {
        byte[] encryptedBytes = Base64.getDecoder().decode(base64EncryptedAesKey);
        Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, localPrivateKey);
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new SecretKeySpec(decryptedBytes, AES_ALGORITHM);
    }

    /**
     * Encrypts plaintext using AES-256-GCM.
     * The resulting payload contains: [12-byte IV][ciphertext + tag] encoded in Base64.
     */
    public static String encryptMessage(String plaintext, SecretKey aesKey) throws Exception {
        byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] iv = new byte[GCM_IV_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, spec);
        byte[] ciphertext = cipher.doFinal(plaintextBytes);

        // Prepend IV to ciphertext
        byte[] payload = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, payload, 0, iv.length);
        System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);

        return Base64.getEncoder().encodeToString(payload);
    }

    /**
     * Decrypts a Base64 payload containing [12-byte IV][ciphertext + tag] using AES-256-GCM.
     */
    public static String decryptMessage(String base64Payload, SecretKey aesKey) throws Exception {
        byte[] payload = Base64.getDecoder().decode(base64Payload);
        if (payload.length < GCM_IV_LENGTH) {
            throw new IllegalArgumentException("Payload too short; missing IV");
        }

        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] ciphertext = new byte[payload.length - GCM_IV_LENGTH];
        System.arraycopy(payload, 0, iv, 0, GCM_IV_LENGTH);
        System.arraycopy(payload, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, aesKey, spec);
        byte[] decryptedBytes = cipher.doFinal(ciphertext);

        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }
}
