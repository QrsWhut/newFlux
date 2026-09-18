package com.example.chat.common.security;

import com.example.chat.common.exception.IdentityConfigurationException;
import com.example.chat.common.exception.InvalidOpenAiRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * HMAC 受信身份请求头解析测试。
 */
class HeaderRequestIdentityResolverTest {

    /** 测试共享密钥。 */
    private static final String TEST_SECRET = "0123456789abcdef0123456789abcdef";
    /** HMAC 算法。 */
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    /** 固定测试时间。 */
    private static final Instant FIXED_TIME = Instant.parse("2026-08-25T08:00:00Z");

    /**
     * 验证合法时效内的 HMAC 身份头。
     *
     * @throws NoSuchAlgorithmException 测试运行时缺少 HMAC 算法
     * @throws InvalidKeyException 测试密钥无效
     */
    @Test
    void shouldResolveSignedIdentity()
            throws NoSuchAlgorithmException, InvalidKeyException {
        Clock clock = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);
        HeaderRequestIdentityResolver resolver =
                new HeaderRequestIdentityResolver(TEST_SECRET, clock);
        String timestamp = Long.toString(FIXED_TIME.getEpochSecond());
        String signature = sign(timestamp, "user-001", TEST_SECRET);
        MockServerHttpRequest request = signedRequest(timestamp, "user-001", signature);

        RequestIdentity identity = resolver.resolve(request);

        assertEquals("user-001", identity.userId());
    }

    /**
     * 验证错误签名被拒绝。
     */
    @Test
    void shouldRejectForgedSignature() {
        Clock clock = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);
        HeaderRequestIdentityResolver resolver =
                new HeaderRequestIdentityResolver(TEST_SECRET, clock);
        String timestamp = Long.toString(FIXED_TIME.getEpochSecond());
        MockServerHttpRequest request = signedRequest(
                timestamp,
                "user-001",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        assertThrows(InvalidOpenAiRequestException.class, () -> resolver.resolve(request));
    }

    /**
     * 验证过期签名被拒绝。
     *
     * @throws NoSuchAlgorithmException 测试运行时缺少 HMAC 算法
     * @throws InvalidKeyException 测试密钥无效
     */
    @Test
    void shouldRejectExpiredSignature()
            throws NoSuchAlgorithmException, InvalidKeyException {
        Clock clock = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);
        HeaderRequestIdentityResolver resolver =
                new HeaderRequestIdentityResolver(TEST_SECRET, clock);
        String timestamp = Long.toString(FIXED_TIME.minusSeconds(600L).getEpochSecond());
        String signature = sign(timestamp, "user-001", TEST_SECRET);
        MockServerHttpRequest request = signedRequest(timestamp, "user-001", signature);

        assertThrows(InvalidOpenAiRequestException.class, () -> resolver.resolve(request));
    }

    /**
     * 验证缺少安全密钥时仅拒绝请求，不影响解析器实例化。
     */
    @Test
    void shouldFailClosedWhenSecretIsMissing() {
        Clock clock = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);
        HeaderRequestIdentityResolver resolver = new HeaderRequestIdentityResolver(null, clock);
        MockServerHttpRequest request = MockServerHttpRequest.post("/v1/responses").build();

        assertThrows(IdentityConfigurationException.class, () -> resolver.resolve(request));
    }

    /**
     * 构建带身份签名的模拟请求。
     *
     * @param timestamp 秒级 Unix 时间戳
     * @param userId 用户标识
     * @param signature HMAC 签名
     * @return 模拟请求
     */
    private MockServerHttpRequest signedRequest(
            String timestamp,
            String userId,
            String signature) {
        return MockServerHttpRequest.post("/v1/responses")
                .header(HeaderRequestIdentityResolver.USER_ID_HEADER, userId)
                .header(HeaderRequestIdentityResolver.TIMESTAMP_HEADER, timestamp)
                .header(HeaderRequestIdentityResolver.SIGNATURE_HEADER, signature)
                .build();
    }

    /**
     * 生成测试 HMAC 签名。
     *
     * @param timestamp 秒级 Unix 时间戳
     * @param userId 用户标识
     * @param secret 共享密钥
     * @return 十六进制 HMAC 签名
     * @throws NoSuchAlgorithmException 测试运行时缺少 HMAC 算法
     * @throws InvalidKeyException 测试密钥无效
     */
    private String sign(String timestamp, String userId, String secret)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        byte[] signature = mac.doFinal(
                (timestamp + "\n" + userId).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(signature);
    }
}
