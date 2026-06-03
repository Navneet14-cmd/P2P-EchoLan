package com.robustians.echolan.crypto;

import static org.junit.jupiter.api.Assertions.*;

import java.security.KeyPair;
import java.security.PublicKey;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

public class CryptoEngineTest {

    @Test
    public void testRsaKeyPairGenerationAndSerialization() throws Exception {
        KeyPair kp = CryptoEngine.generateRsaKeyPair();
        assertNotNull(kp.getPublic());
        assertNotNull(kp.getPrivate());

        String serializedPubKey = CryptoEngine.encodePublicKey(kp.getPublic());
        assertNotNull(serializedPubKey);
        assertFalse(serializedPubKey.isEmpty());

        PublicKey reconstructedPubKey = CryptoEngine.decodePublicKey(serializedPubKey);
        assertArrayEquals(kp.getPublic().getEncoded(), reconstructedPubKey.getEncoded());
    }

    @Test
    public void testAesKeyWrappingAndUnwrapping() throws Exception {
        KeyPair kp = CryptoEngine.generateRsaKeyPair();
        SecretKey originalAesKey = CryptoEngine.generateAesKey();

        String encryptedAesKey = CryptoEngine.encryptAesKey(originalAesKey, kp.getPublic());
        assertNotNull(encryptedAesKey);

        SecretKey decryptedAesKey = CryptoEngine.decryptAesKey(encryptedAesKey, kp.getPrivate());
        assertArrayEquals(originalAesKey.getEncoded(), decryptedAesKey.getEncoded());
    }

    @Test
    public void testMessageEncryptionAndDecryption() throws Exception {
        SecretKey aesKey = CryptoEngine.generateAesKey();
        String originalMessage = "Hello World! This is a test message. Standard ASCII + UTF-8: 🧑‍💻 🎉 🚀";

        String ciphertextB64 = CryptoEngine.encryptMessage(originalMessage, aesKey);
        assertNotNull(ciphertextB64);
        assertNotEquals(originalMessage, ciphertextB64);

        String decryptedMessage = CryptoEngine.decryptMessage(ciphertextB64, aesKey);
        assertEquals(originalMessage, decryptedMessage);
    }
}
