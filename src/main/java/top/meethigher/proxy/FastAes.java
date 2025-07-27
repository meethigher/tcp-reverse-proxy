package top.meethigher.proxy;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 高性能 AES/GCM 对称加解密工具类（仅依赖 JDK 标准库）
 */
public final class FastAes {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int AES_KEY_LEN = 128;          // bit
    private static final int GCM_TAG_LEN = 128;          // bit
    private static final int GCM_IV_LEN = 12;           // byte

    private static final SecureRandom RAND = new SecureRandom();

    /* 每个线程复用自己的 Cipher 实例 */
    private static final ThreadLocal<Cipher> CIPHER_HOLDER = ThreadLocal.withInitial(() -> {
        try {
            return Cipher.getInstance(TRANSFORMATION);
        } catch (Exception e) {
            throw new RuntimeException("AES/GCM not available", e);
        }
    });

    private FastAes() {
    }   // utility class

    /* ---------------------------------- 对外 API ---------------------------------- */

    /**
     * 随机生成 AES-128 密钥
     *
     * @return 密钥
     */
    public static SecretKey generateKey() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(AES_KEY_LEN);
            return kg.generateKey();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 将原始密钥字节数组包装成 SecretKey
     *
     * @param rawKey 原始密钥字节数组
     * @return SecretKey
     */
    public static SecretKey restoreKey(byte[] rawKey) {
        if (rawKey.length != AES_KEY_LEN / 8) {
            throw new IllegalArgumentException("Key length != 16 byte");
        }
        return new SecretKeySpec(rawKey, "AES");
    }

    /**
     * 加密：返回 byte[]，格式为 IV(12B) + CipherText + Tag(16B)
     *
     * @param key   密钥
     * @param plain 原文
     * @return 密文字节数组
     */
    public static byte[] encrypt(byte[] plain, SecretKey key) {
        try {
            byte[] iv = new byte[GCM_IV_LEN];
            RAND.nextBytes(iv);

            Cipher cipher = CIPHER_HOLDER.get();
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LEN, iv));

            byte[] cipherText = cipher.doFinal(plain);

            return ByteBuffer.allocate(iv.length + cipherText.length)
                    .put(iv)
                    .put(cipherText)
                    .array();
        } catch (Exception e) {
            throw new RuntimeException("Encrypt error", e);
        }
    }

    /**
     * 解密：输入格式须为 IV(12B) + CipherText + Tag(16B)
     *
     * @param key              密钥
     * @param ivPlusCipherText 密文
     * @return 原文
     */
    public static byte[] decrypt(byte[] ivPlusCipherText, SecretKey key) {
        try {
            if (ivPlusCipherText.length < GCM_IV_LEN) {
                throw new IllegalArgumentException("Bad input length");
            }
            ByteBuffer buf = ByteBuffer.wrap(ivPlusCipherText);

            byte[] iv = new byte[GCM_IV_LEN];
            buf.get(iv);

            byte[] cipherAndTag = new byte[buf.remaining()];
            buf.get(cipherAndTag);

            Cipher cipher = CIPHER_HOLDER.get();
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LEN, iv));

            return cipher.doFinal(cipherAndTag);
        } catch (Exception e) {
            throw new RuntimeException("Decrypt error", e);
        }
    }


    public static String encryptToBase64(byte[] plain, SecretKey key) {
        return Base64.getEncoder().encodeToString(encrypt(plain, key));
    }

    public static byte[] decryptFromBase64(String base64, SecretKey key) {
        return decrypt(Base64.getDecoder().decode(base64), key);
    }

}