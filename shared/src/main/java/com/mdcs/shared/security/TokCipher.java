package com.mdcs.shared.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.util.Base64;

public class TokCipher {
    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH = 128;
    private static SecretKey secret_key;

    static {
        try {
            secret_key = KeyManager.getKey();
        } catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    public static String encrypt(String plainText) {
        try {
            // Initiate Cipher object with GCM mode (AES algorithm)
            Cipher cipher = Cipher.getInstance(ALGO);

            // Get randome bytes which makes the output string random everytime
            byte[] random_bytes = new byte[12];
            new java.security.SecureRandom().nextBytes(random_bytes);

            // Set the required settings for cipher object
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, random_bytes);

            // Set all the configs
            cipher.init(Cipher.ENCRYPT_MODE, secret_key, spec);

            // Start the encrytion
            byte[] encrypted = cipher.doFinal(plainText.getBytes());

            // Combine the IV and encrypted bytes to produce a new byte array:
            // [ IV ][ encrypted ]
            // Adding IV at the start helps us to get the IV used, during the decryption
            byte[] combined = new byte[random_bytes.length + encrypted.length];
            System.arraycopy(random_bytes, 0, combined, 0, random_bytes.length);
            System.arraycopy(encrypted, 0, combined, random_bytes.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    public static String decrypt(String cipherText) {
        try {
            // Convert plain string to bytes
            byte[] decoded = Base64.getDecoder().decode(cipherText);

            byte[] random_bytes = new byte[12];
            byte[] encrypted = new byte[decoded.length - 12];

            // Extract the IV used during encryption
            System.arraycopy(decoded, 0, random_bytes, 0, 12);

            // Extract the encrypted bytes into encrypted
            System.arraycopy(decoded, 12, encrypted, 0, encrypted.length);

            // Initiate Cipher object with GCM mode (AES algorithm)
            Cipher cipher = Cipher.getInstance(ALGO);

            // Set the required settings for cipher object
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, random_bytes);

            // Set the configs for the cipher objects
            cipher.init(Cipher.DECRYPT_MODE, secret_key, spec);

            // Get the decoded array of bytes
            byte[] plain = cipher.doFinal(encrypted);

            return new String(plain);
        } catch (Exception e){
            throw new RuntimeException(e);
        }
    }
}