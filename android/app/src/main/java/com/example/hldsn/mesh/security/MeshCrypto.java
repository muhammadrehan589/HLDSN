package com.example.hldsn.mesh.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class MeshCrypto {

    private static final int GCM_NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private MeshCrypto() {
    }

    public static KeyPair generateEcKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        return generator.generateKeyPair();
    }

    public static String encodeKey(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    public static String encodeKey(PrivateKey privateKey) {
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    public static PublicKey decodePublicKey(String base64) throws GeneralSecurityException {
        byte[] encoded = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(encoded));
    }

    public static PrivateKey decodePrivateKey(String base64) throws GeneralSecurityException {
        byte[] encoded = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    public static String encrypt(String senderPrivateKey, String recipientPublicKey, String clearText)
            throws GeneralSecurityException {
        byte[] payload = clearText.getBytes(StandardCharsets.UTF_8);
        byte[] nonce = new byte[GCM_NONCE_BYTES];
        new SecureRandom().nextBytes(nonce);

        SecretKey key = deriveAesKey(senderPrivateKey, recipientPublicKey, nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
        byte[] encrypted = cipher.doFinal(payload);

        ByteBuffer buffer = ByteBuffer.allocate(nonce.length + encrypted.length);
        buffer.put(nonce);
        buffer.put(encrypted);
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    public static String decrypt(String recipientPrivateKey, String senderPublicKey, String encryptedPayload)
            throws GeneralSecurityException {
        byte[] merged = Base64.getDecoder().decode(encryptedPayload);
        if (merged.length <= GCM_NONCE_BYTES) {
            throw new GeneralSecurityException("Encrypted payload too short");
        }

        byte[] nonce = Arrays.copyOfRange(merged, 0, GCM_NONCE_BYTES);
        byte[] cipherBytes = Arrays.copyOfRange(merged, GCM_NONCE_BYTES, merged.length);

        SecretKey key = deriveAesKey(recipientPrivateKey, senderPublicKey, nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
        byte[] clear = cipher.doFinal(cipherBytes);
        return new String(clear, StandardCharsets.UTF_8);
    }

    private static SecretKey deriveAesKey(String ourPrivateKey, String peerPublicKey, byte[] nonce)
            throws GeneralSecurityException {
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(decodePrivateKey(ourPrivateKey));
        agreement.doPhase(decodePublicKey(peerPublicKey), true);
        byte[] secret = agreement.generateSecret();

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(secret);
        digest.update(nonce);
        byte[] key = digest.digest();
        return new SecretKeySpec(key, 0, 16, "AES");
    }
}

