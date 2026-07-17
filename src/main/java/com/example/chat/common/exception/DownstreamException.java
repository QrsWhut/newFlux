package com.example.chat.common.exception;

/**
 * 统一的下游微服务调用异常类
 *
 * @author Antigravity
 * @since 2026-07-17
 */
public class DownstreamException extends RuntimeException {

    public enum ErrorType {
        CONNECT_TIMEOUT,
        RESPONSE_TIMEOUT,
        HTTP_CLIENT_ERROR,
        HTTP_SERVER_ERROR,
        PARSE_ERROR,
        CANCELLED,
        UNKNOWN
    }

    private final String downstreamName;
    private final int httpStatus;
    private final ErrorType errorType;
    private final boolean retryable;
    private final boolean degraded;

    public DownstreamException(String downstreamName, int httpStatus, ErrorType errorType, 
                               boolean retryable, boolean degraded, String message) {
        super(String.format("[%s] 下游接口异常, HTTP Status %d, Type %s: %s", 
                downstreamName, httpStatus, errorType, message));
        this.downstreamName = downstreamName;
        this.httpStatus = httpStatus;
        this.errorType = errorType;
        this.retryable = retryable;
        this.degraded = degraded;
    }

    public DownstreamException(String downstreamName, int httpStatus, ErrorType errorType, 
                               boolean retryable, boolean degraded, String message, Throwable cause) {
        super(String.format("[%s] 下游接口异常, HTTP Status %d, Type %s: %s", 
                downstreamName, httpStatus, errorType, message), cause);
        this.downstreamName = downstreamName;
        this.httpStatus = httpStatus;
        this.errorType = errorType;
        this.retryable = retryable;
        this.degraded = degraded;
    }

    public String getDownstreamName() {
        return downstreamName;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public boolean isDegraded() {
        return degraded;
    }
}
