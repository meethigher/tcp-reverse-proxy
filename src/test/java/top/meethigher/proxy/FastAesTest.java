package top.meethigher.proxy;

import org.junit.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static top.meethigher.proxy.FastAes.*;

public class FastAesTest {
    @Test
    public void name() {


//        System.out.println(new String(generateKey().getEncoded(), StandardCharsets.UTF_8));

        String secret = "1234567890abcdef";
        String text = "meethigher";

        text = "k2p7c9x3f5q8b1n6m4r3v7y9t2e5w8a1s4d6g7j9k3f5h8q2n4b6v8c1x3z5a7s9e2w4r6t8y0u3i5o7p9a2s4d6f8g1h3j5k7l9z2x4c6v8b0n1m3q5w7e9r2t4y6u8i0o3p5a7s9d2f4g6h8j0k3l5z7x9c2v4b6n8m0q1w3e5r7t9y2u4i6o8p0a3s5d7f9g2h4j6k8l0z3x5c7v9b2n4m6q8w0e3r5t7y9u2i4o6p8a0s3d5f7g9h2j4k6l8z0x3c5v7b9n2m4q6w8e0r3t5y7u9i2o4p6a8s0d3f5g7h9j2k4l6z8x0c3v5b7n9m2q4w6e8r0t3y5u7i9o2p4a6s8d0f3g5h7j9k2l4z6x8c0v3b5n7m9q2w4e6r8t0y3u5i7o9p2a4s6d8f0g3h5j7k9l2z4x6c8v0b3n5m7q9w2e4r6t8y0u3i5o7p9a2s4d6f8g0h3j5k7l9z2x4c6v8b0n3m5q7w9e2r4t6y8u0i3o5p7a9s2d4f6g8h0j3k5l7z9x2c4v6b8n0m3q5w7e9r2t4y6u8i0o3p5a7s9d2f4g6h8j0k3l5z7x9c2v4b6n8m0q1w3e5r7t9y2u4i6o8p0a3s5d7f9g2h4j6k8l0z3x5c7v9b2n4m6q8w0e3r5t7y9u2i4o6p8a0s3d5f7g9h2j4k6l8z0x3c5v7b9n2m4q6w8e0r3t5y7u9i2o4p6a8s0d3f5g7h9j2k4l6z8x0c3v5b7n9m2q4w6e8r0t3y5u7i9o2p4a6s8d0f3g5h7j9k2l4z6x8c0v3b5n7m9q2w4e6r8t0y3u5i7o9p2a4s6d8f0g3h5j7k9l2z4x6c8v0b3n5m7q9w2e4r6t8y0u3i5o7p9a2s4d6f8g0h3j5k7l9z2x4c6";

        SecretKey key = restoreKey(secret.getBytes(StandardCharsets.UTF_8));

        System.out.println(key.toString());

        // 加密
        byte[] cipher = encrypt(text.getBytes(StandardCharsets.UTF_8), key);
        System.out.println("Cipher len: " + cipher.length);

        String base64 = encryptToBase64(text.getBytes(StandardCharsets.UTF_8), key);
        System.out.println("Cipher: " + base64);

        // 解密
        byte[] plain = decrypt(cipher, key);
        System.out.println("Plain : " + new String(plain, StandardCharsets.UTF_8));
        byte[] bytes = decryptFromBase64(base64, key);
        System.out.println("Plain : " + new String(bytes, StandardCharsets.UTF_8));

    }
}