package com.example.chat.config;

import com.example.chat.common.enums.FinancialProviderMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Wind 金融能力运行配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "wind")
public class WindProperties {

    /** 金融能力提供方选择模式。 */
    private FinancialProviderMode providerMode;
    /** MCP 初始化请求超时时间。 */
    private Duration initializeTimeout;
    /** MCP 工具调用超时时间。 */
    private Duration callTimeout;

    /**
     * 获取安全的提供方模式，未配置时只使用现有能力。
     *
     * @return 提供方模式
     */
    public FinancialProviderMode resolveProviderMode() {
        return providerMode == null ? FinancialProviderMode.LEGACY_ONLY : providerMode;
    }

    /**
     * 获取 MCP 初始化超时时间。
     *
     * @return 初始化超时时间
     */
    public Duration resolveInitializeTimeout() {
        return initializeTimeout == null ? Duration.ofSeconds(30L) : initializeTimeout;
    }

    /**
     * 获取 MCP 工具调用超时时间。
     *
     * @return 工具调用超时时间
     */
    public Duration resolveCallTimeout() {
        return callTimeout == null ? Duration.ofMinutes(10L) : callTimeout;
    }
}
