package com.example.chat.common.enums;

/**
 * 金融能力提供方统一错误码。
 */
public enum FinancialProviderErrorCode {

    /** 认证信息缺失或无效。 */
    AUTHENTICATION_FAILED,
    /** 提供方触发限流。 */
    RATE_LIMITED,
    /** 提供方额度不足。 */
    QUOTA_EXCEEDED,
    /** 请求参数无效。 */
    INVALID_REQUEST,
    /** 请求能力超出提供方范围。 */
    OUT_OF_SCOPE,
    /** 提供方返回业务错误。 */
    PROVIDER_ERROR,
    /** 网络连接失败。 */
    NETWORK_ERROR,
    /** 请求超时。 */
    TIMEOUT,
    /** 响应格式无效。 */
    INVALID_RESPONSE
}
