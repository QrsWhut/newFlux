package com.example.chat.common.enums;

/**
 * 金融能力提供方选择模式。
 */
public enum FinancialProviderMode {

    /** 仅调用现有金融能力。 */
    LEGACY_ONLY,
    /** 仅调用 Wind 金融能力。 */
    WIND_ONLY,
    /** 优先调用 Wind，只有允许降级的错误才回退现有能力。 */
    WIND_PREFERRED
}
