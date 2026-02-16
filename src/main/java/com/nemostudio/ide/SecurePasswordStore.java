package com.nemostudio.ide;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;

/**
 * Stores and retrieves the remote connection password encrypted on disk.
 * Uses AES-256-GCM with a key derived via PBKDF2 from machine-bound data (no user master password).
 * Only call save after connection success; load fills the password field when reopening the window.
 */
public final class SecurePasswordStore {

    private static final String DIR_NAME = ".nemostudio";
    private static final String FILE_NAME = "credentials.enc";
    private static final int SALT_LEN = 16;
    private static final int IV_LEN = 12;
    private static final int GCM_TAG_LEN = 128;
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int KEY_LEN_BITS = 256;
    private static final String ALGORITHM = "AES/GCM/NoPadding";

    private SecurePasswordStore() {}

    public static Path getCredentialPath() {
        return Path.of(System.getProperty("user.home")).resolve(DIR_NAME).resolve(FILE_NAME);
    }

    /**
     * Derive a secret key from machine-bound data so the credential is only decryptable on this machine.
     */
    private static byte[] getMachineSalt() {
        String home = System.getProperty("user.home", "");
        String os = System.getProperty("os.name", "");
        return (home + os + "NemoStudio").getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Saves the password encrypted. Overwrites any existing file. Call only after connection success.
     */
    public static void savePassword(char[] password) throws Exception {
        if (password == null || password.length == 0) {
            deleteSavedPassword();
            return;
        }
        Path path = getCredentialPath();
        Path dir = path.getParent();
        if (dir != null && !Files.isDirectory(dir)) {
            Files.createDirectories(dir);
        }
        SecureRandom rng = new SecureRandom();
        byte[] salt = new byte[SALT_LEN];
        rng.nextBytes(salt);
        byte[] iv = new byte[IV_LEN];
        rng.nextBytes(iv);
        SecretKey key = deriveKey(salt);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LEN, iv));
        byte[] plain = charsToBytes(password);
        byte[] ciphertext = cipher.doFinal(plain);
        wipe(plain);
        ByteBuffer buf = ByteBuffer.allocate(SALT_LEN + IV_LEN + ciphertext.length);
        buf.put(salt);
        buf.put(iv);
        buf.put(ciphertext);
        Files.write(path, buf.array());
    }

    /**
     * Loads and decrypts the saved password. Returns null if no file or decryption fails.
     */
    public static char[] loadPassword() {
        Path path = getCredentialPath();
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            byte[] raw = Files.readAllBytes(path);
            if (raw.length < SALT_LEN + IV_LEN + 1) {
                return null;
            }
            ByteBuffer buf = ByteBuffer.wrap(raw);
            byte[] salt = new byte[SALT_LEN];
            buf.get(salt);
            byte[] iv = new byte[IV_LEN];
            buf.get(iv);
            byte[] ciphertext = new byte[buf.remaining()];
            buf.get(ciphertext);
            SecretKey key = deriveKey(salt);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LEN, iv));
            byte[] plain = cipher.doFinal(ciphertext);
            char[] result = bytesToChars(plain);
            wipe(plain);
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    public static void deleteSavedPassword() {
        try {
            Files.deleteIfExists(getCredentialPath());
        } catch (IOException ignored) {}
    }

    private static SecretKey deriveKey(byte[] salt) throws Exception {
        byte[] machine = getMachineSalt();
        char[] machineChars = new String(machine, java.nio.charset.StandardCharsets.UTF_8).toCharArray();
        KeySpec spec = new PBEKeySpec(
                machineChars,
                salt,
                PBKDF2_ITERATIONS,
                KEY_LEN_BITS
        );
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = f.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    private static byte[] charsToBytes(char[] c) {
        return new String(c).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static char[] bytesToChars(byte[] b) {
        return new String(b, java.nio.charset.StandardCharsets.UTF_8).toCharArray();
    }

    private static void wipe(byte[] a) {
        if (a != null) Arrays.fill(a, (byte) 0);
    }
}
