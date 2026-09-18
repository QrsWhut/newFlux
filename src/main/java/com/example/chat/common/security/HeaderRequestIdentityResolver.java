package com.example.chat.common.security;

import com.example.chat.common.exception.IdentityConfigurationException;
import com.example.chat.common.exception.InvalidOpenAiRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * 校验网关 HMAC 签名后，从受信请求头解析用户身份。
 *
 * <p>网关必须剥离外部同名请求头，并使用共享密钥重新签名。服务在密钥缺失、签名失效或请求过期时
 * 一律拒绝请求，避免调用方伪造用户身份。</p>
 */
@Component
public class HeaderRequestIdentityResolver implements RequestIdentityResolver {

    /** 受信用户身份请求头。 */
    public static final String USER_ID_HEADER = "X-User-Id";
    /** 身份签名时间戳请求头。 */
    public static final String TIMESTAMP_HEADER = "X-Identity-Timestamp";
    /** 身份签名请求头。 */
    public static final String SIGNATURE_HEADER = "X-Identity-Signature";
    /** 身份签名密钥环境变量。 */
    public static final String SECRET_PROPERTY = "TRUSTED_IDENTITY_HMAC_SECRET";
    /** HMAC 算法名称。 */
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    /** 用户标识最大长度。 */
    private static final int MAX_USER_ID_LENGTH = 128;
    /** 最小共享密钥字节数。 */
    private static final int MINIMUM_SECRET_BYTES = 32;
    /** HMAC-SHA256 签名字节数。 */
    private static final int HMAC_SIGNATURE_BYTES = 32;
    /** 身份签名最大时钟偏差。 */
    private static final Duration MAXIMUM_CLOCK_SKEW = Duration.ofMinutes(5L);
    /** 共享签名密钥。 */
    private final byte[] secret;
    /** 可测试时钟。 */
    private final Clock clock;

    /**
     * 使用运行环境中的共享密钥创建解析器。
     *
     * @param environment Spring 运行环境
     */
    @Autowired
    public HeaderRequestIdentityResolver(Environment environment) {
        this(environment.getProperty(SECRET_PROPERTY), Clock.systemUTC());
    }

    /**
     * 使用指定共享密钥和时钟创建解析器。
     *
     * @param configuredSecret 共享签名密钥
     * @param clock 可测试时钟
     */
    HeaderRequestIdentityResolver(String configuredSecret, Clock clock) {
        this.secret = configuredSecret == null
                ? new byte[0] : configuredSecret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    /**
     * 校验 HMAC 签名并解析用户身份。
     *
     * @param request 服务端请求
     * @return 已认证请求身份
     */
    @Override
    public RequestIdentity resolve(ServerHttpRequest request) {
        validateSecret();
        String userId = requireHeader(request, USER_ID_HEADER).trim();
        if (userId.isEmpty() || userId.length() > MAX_USER_ID_LENGTH) {
            throw new InvalidOpenAiRequestException(USER_ID_HEADER + "格式不合法");
        }
        String timestampText = requireHeader(request, TIMESTAMP_HEADER);
        Instant signedAt = parseTimestamp(timestampText);
        validateTimestamp(signedAt);
        byte[] providedSignature = parseSignature(requireHeader(request, SIGNATURE_HEADER));
        byte[] expectedSignature = sign(timestampText, userId);
        if (!MessageDigest.isEqual(expectedSignature, providedSignature)) {
            throw new InvalidOpenAiRequestException("请求身份签名无效");
        }
        return new RequestIdentity(userId);
    }

    /**
     * 校验服务端共享密钥是否达到安全下限。
     */
    private void validateSecret() {
        if (secret.length < MINIMUM_SECRET_BYTES) {
            throw new IdentityConfigurationException("服务端身份签名密钥未配置或长度不足");
        }
    }

    /**
     * 获取必填请求头。
     *
     * @param request 服务端请求
     * @param headerName 请求头名称
     * @return 请求头内容
     */
    private String requireHeader(ServerHttpRequest request, String headerName) {
        String value = request.getHeaders().getFirst(headerName);
        if (value == null || value.isBlank()) {
            throw new InvalidOpenAiRequestException(headerName + "不能为空");
        }
        return value;
    }

    /**
     * 解析签名时间戳。
     *
     * @param timestampText 秒级 Unix 时间戳
     * @return 签名时间
     */
    private Instant parseTimestamp(String timestampText) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(timestampText));
        } catch (NumberFormatException | DateTimeException exception) {
            throw new InvalidOpenAiRequestException("身份签名时间戳格式不合法");
        }
    }

    /**
     * 校验签名时间是否位于允许窗口内。
     *
     * @param signedAt 签名时间
     */
    private void validateTimestamp(Instant signedAt) {
        Duration clockSkew = Duration.between(signedAt, clock.instant()).abs();
        if (clockSkew.compareTo(MAXIMUM_CLOCK_SKEW) > 0) {
            throw new InvalidOpenAiRequestException("请求身份签名已过期");
        }
    }

    /**
     * 解析十六进制 HMAC 签名。
     *
     * @param signatureText 十六进制签名
     * @return 签名字节
     */
    private byte[] parseSignature(String signatureText) {
        try {
            byte[] signature = HexFormat.of().parseHex(signatureText);
            if (signature.length != HMAC_SIGNATURE_BYTES) {
                throw new InvalidOpenAiRequestException("请求身份签名长度不合法");
            }
            return signature;
        } catch (IllegalArgumentException exception) {
            throw new InvalidOpenAiRequestException("请求身份签名格式不合法");
        }
    }

    /**
     * 计算规范身份载荷的 HMAC 签名。
     *
     * @param timestampText 秒级 Unix 时间戳
     * @param userId 用户标识
     * @return HMAC 签名
     */
    private byte[] sign(String timestampText, String userId) {
        String payload = timestampText + "\n" + userId;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行时不支持HmacSHA256", exception);
        } catch (InvalidKeyException exception) {
            throw new IllegalStateException("身份签名密钥不可用", exception);
        }
    }
}
