package com.example.chat.common.exception;

import com.example.chat.common.enums.FinancialProvider;
import com.example.chat.common.enums.FinancialProviderErrorCode;
import lombok.Getter;

/**
 * 金融能力提供方统一异常。
 */
@Getter
public class FinancialProviderException extends RuntimeException {

    /** 金融能力提供方。 */
    private final FinancialProvider provider;
    /** 结构化错误码。 */
    private final FinancialProviderErrorCode errorCode;
    /** 是否允许稍后重试。 */
    private final boolean retryable;

    /**
     * 创建金融能力提供方异常。
     *
     * @param provider 金融能力提供方
     * @param errorCode 结构化错误码
     * @param message 安全错误信息
     * @param retryable 是否允许稍后重试
     */
    public FinancialProviderException(FinancialProvider provider,
            FinancialProviderErrorCode errorCode, String message, boolean retryable) {
        super(message);
        this.provider = provider;
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    /**
     * 判断复合网关是否允许回退现有能力。
     *
     * @return 是否允许回退
     */
    public boolean isFallbackAllowed() {
        return errorCode == FinancialProviderErrorCode.NETWORK_ERROR
                || errorCode == FinancialProviderErrorCode.TIMEOUT
                || errorCode == FinancialProviderErrorCode.PROVIDER_ERROR
                || errorCode == FinancialProviderErrorCode.INVALID_RESPONSE
                || errorCode == FinancialProviderErrorCode.OUT_OF_SCOPE;
    }
}
