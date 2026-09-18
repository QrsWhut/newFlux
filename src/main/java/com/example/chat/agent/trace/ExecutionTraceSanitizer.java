package com.example.chat.agent.trace;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.example.chat.agent.tool.AgentToolResult;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 执行轨迹敏感字段脱敏与容量治理工具。
 */
public final class ExecutionTraceSanitizer {

    /** 参数持久化最大字符数。 */
    public static final int MAX_ARGUMENTS_LENGTH = 4_096;
    /** 工具结果摘要最大字符数。 */
    public static final int MAX_RESULT_SUMMARY_LENGTH = 2_048;
    /** 错误摘要最大字符数。 */
    public static final int MAX_ERROR_MESSAGE_LENGTH = 1_024;
    /** 截断标记。 */
    private static final String TRUNCATED_SUFFIX = "...【已截断】";
    /** 敏感字段替换值。 */
    private static final String REDACTED_VALUE = "***";
    /** 需要整体脱敏的规范化字段名。 */
    private static final Set<String> SENSITIVE_FIELD_NAMES = Set.of(
            "key",
            "apikey",
            "accesstoken",
            "authorization",
            "password",
            "secret",
            "clientsecret",
            "credential",
            "privatekey");
    /** 非 JSON 文本中的凭证赋值模式。 */
    private static final Pattern CREDENTIAL_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?i)((?:api[_-]?key|access[_-]?token|authorization|password|secret|credential)"
                    + "\\s*[:=]\\s*)([^,\\s}]+)");
    /** Authorization Bearer 模式。 */
    private static final Pattern BEARER_PATTERN = Pattern.compile(
            "(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+");
    /** 常见密钥前缀模式。 */
    private static final Pattern SECRET_TOKEN_PATTERN = Pattern.compile(
            "\\b(?:sk|key)-[A-Za-z0-9_-]{8,}\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * 禁止实例化无状态工具类。
     */
    private ExecutionTraceSanitizer() {
    }

    /**
     * 对工具参数进行结构化脱敏并限制长度。
     *
     * @param argumentsContent 原始参数文本
     * @return 可持久化参数文本
     */
    public static String sanitizeArguments(String argumentsContent) {
        return sanitizeText(argumentsContent, MAX_ARGUMENTS_LENGTH);
    }

    /**
     * 对工具结果生成受控持久化摘要。
     *
     * @param result 工具结果
     * @return 可持久化结果摘要
     */
    public static String sanitizeToolResult(AgentToolResult result) {
        if (result == null) {
            return "";
        }
        return sanitizeText(result.toModelObservation(), MAX_RESULT_SUMMARY_LENGTH);
    }

    /**
     * 对错误信息进行脱敏并限制长度。
     *
     * @param errorMessage 原始错误信息
     * @return 可持久化错误摘要
     */
    public static String sanitizeErrorMessage(String errorMessage) {
        return sanitizeText(errorMessage, MAX_ERROR_MESSAGE_LENGTH);
    }

    private static String sanitizeText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String sanitized = sanitizeStructuredValue(value.trim());
        return truncate(sanitized, maxLength);
    }

    private static String sanitizeStructuredValue(String value) {
        try {
            Object parsed = JSON.parse(value);
            sanitizeParsedValue(parsed);
            return JSON.toJSONString(parsed);
        } catch (JSONException exception) {
            return redactPlainText(value);
        }
    }

    private static void sanitizeParsedValue(Object value) {
        if (value instanceof JSONObject object) {
            for (String key : Set.copyOf(object.keySet())) {
                Object fieldValue = object.get(key);
                if (isSensitiveField(key)) {
                    object.put(key, REDACTED_VALUE);
                } else {
                    object.put(key, sanitizeNestedValue(fieldValue));
                }
            }
            return;
        }
        if (value instanceof JSONArray array) {
            for (int index = 0; index < array.size(); index++) {
                array.set(index, sanitizeNestedValue(array.get(index)));
            }
        }
    }

    private static Object sanitizeNestedValue(Object value) {
        if (value instanceof JSONObject || value instanceof JSONArray) {
            sanitizeParsedValue(value);
            return value;
        }
        if (!(value instanceof String text)) {
            return value;
        }
        String trimmed = text.trim();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            return sanitizeStructuredValue(trimmed);
        }
        return redactPlainText(text);
    }

    private static boolean isSensitiveField(String fieldName) {
        String normalizedName = fieldName == null
                ? "" : fieldName.replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(Locale.ROOT);
        return SENSITIVE_FIELD_NAMES.contains(normalizedName)
                || normalizedName.endsWith("apikey")
                || normalizedName.endsWith("accesstoken")
                || normalizedName.endsWith("password")
                || normalizedName.endsWith("secret");
    }

    private static String redactPlainText(String value) {
        String sanitized = CREDENTIAL_ASSIGNMENT_PATTERN.matcher(value)
                .replaceAll("$1" + REDACTED_VALUE);
        sanitized = BEARER_PATTERN.matcher(sanitized)
                .replaceAll("Bearer " + REDACTED_VALUE);
        return SECRET_TOKEN_PATTERN.matcher(sanitized)
                .replaceAll(REDACTED_VALUE);
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        int prefixLength = Math.max(0, maxLength - TRUNCATED_SUFFIX.length());
        return value.substring(0, prefixLength) + TRUNCATED_SUFFIX;
    }
}
