package com.example.chat.web.vo;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.example.chat.common.dto.ChatRequest;
import com.example.chat.common.enums.ExecutionMode;
import com.example.chat.common.exception.InvalidOpenAiRequestException;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * OpenAI Responses 入站请求视图对象。
 *
 * <p>客户端传入的历史消息只用于定位最后一条用户文本，不会进入服务端会话记忆。服务端历史始终由
 * conversationId 对应的持久化会话加载。</p>
 */
@Data
public class OpenAiResponseRequestVO {

    /** 默认模型展示标识。 */
    private static final String DEFAULT_MODEL = "configured-default";
    /** 会话标识元数据键。 */
    private static final List<String> SESSION_ID_KEYS = List.of(
            "conversation_id", "conversationId", "session_id", "sessionId");
    /** 任务标识元数据键。 */
    private static final List<String> TASK_ID_KEYS = List.of("task_id", "taskId");
    /** 协议边界生成的会话标识前缀。 */
    private static final String SESSION_ID_PREFIX = "conv_";
    /** 协议边界生成的任务标识前缀。 */
    private static final String TASK_ID_PREFIX = "task_";
    /** OpenAI Responses 入站协议标识。 */
    private static final String PROTOCOL_NAME = "openai-responses";
    /** 输入文本最大字符数。 */
    private static final int MAX_INPUT_CHARS = 200_000;
    /** 元数据键值对最大数量。 */
    private static final int MAX_METADATA_ENTRIES = 64;
    /** 标识与模型名称最大长度。 */
    private static final int MAX_IDENTIFIER_CHARS = 128;
    /** 会话与任务标识允许字符。 */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z0-9._:-]+");
    /** 模型标识允许字符。 */
    private static final Pattern MODEL_PATTERN = Pattern.compile("[A-Za-z0-9._:/-]+");

    /** 调用方请求的模型名称。 */
    private String model;
    /** OpenAI Responses 字符串或消息数组输入。 */
    @NotNull(message = "input不能为空")
    private Object input;
    /** 是否使用 SSE 流式响应。 */
    private Boolean stream;
    /** 协议元数据，仅解析服务端允许的会话与任务标识。 */
    private Map<String, Object> metadata;

    /**
     * 将协议请求转换为内部 Agent 指令。
     *
     * @param trustedUserId 身份边界认证后的用户标识
     * @return 内部对话指令
     */
    public ChatRequest toCommand(String trustedUserId) {
        validateMetadata();
        String question = resolveLastUserText();
        String sessionId = resolveMetadataIdentifier(SESSION_ID_KEYS, SESSION_ID_PREFIX);
        String taskId = resolveMetadataIdentifier(TASK_ID_KEYS, TASK_ID_PREFIX);
        Map<String, Object> attributes = new HashMap<>(4);
        attributes.put("pageData", "");
        attributes.put("smartBodyCode", "");
        attributes.put("model", resolveModel());
        attributes.put("protocol", PROTOCOL_NAME);
        return new ChatRequest(
                taskId,
                sessionId,
                trustedUserId,
                question,
                Collections.emptyList(),
                Collections.unmodifiableMap(attributes),
                ExecutionMode.AGENT);
    }

    /**
     * 判断是否启用流式传输。
     *
     * @return true 表示返回 SSE
     */
    public boolean streamEnabled() {
        return Boolean.TRUE.equals(stream);
    }

    /**
     * 获取校验后的模型展示标识。
     *
     * @return 模型标识
     */
    public String resolveModel() {
        if (model == null || model.isBlank()) {
            return DEFAULT_MODEL;
        }
        String normalizedModel = model.trim();
        if (normalizedModel.length() > MAX_IDENTIFIER_CHARS
                || !MODEL_PATTERN.matcher(normalizedModel).matches()) {
            throw new InvalidOpenAiRequestException("model格式不合法");
        }
        return normalizedModel;
    }

    /**
     * 校验元数据规模，避免协议边界接收无界键值集合。
     */
    private void validateMetadata() {
        if (metadata != null && metadata.size() > MAX_METADATA_ENTRIES) {
            throw new InvalidOpenAiRequestException("metadata键值对数量不能超过64");
        }
    }

    /**
     * 从输入中解析最后一条用户文本。
     *
     * @return 最后一条用户文本
     */
    private String resolveLastUserText() {
        Object normalizedInput = normalizeJson(input);
        String question;
        if (normalizedInput instanceof String text) {
            question = text;
        } else if (normalizedInput instanceof JSONObject message) {
            question = extractUserMessage(message);
        } else if (normalizedInput instanceof JSONArray messages) {
            question = findLastUserMessage(messages);
        } else {
            throw new InvalidOpenAiRequestException("input必须是字符串、消息对象或消息数组");
        }
        if (question == null || question.isBlank()) {
            throw new InvalidOpenAiRequestException("input中缺少有效的user文本");
        }
        String normalizedQuestion = question.trim();
        if (normalizedQuestion.length() > MAX_INPUT_CHARS) {
            throw new InvalidOpenAiRequestException("用户输入长度不能超过200000个字符");
        }
        return normalizedQuestion;
    }

    /**
     * 将 Spring 绑定的集合对象转换为 FastJSON 节点。
     *
     * @param value 原始输入
     * @return FastJSON 节点或原始字符串
     */
    private Object normalizeJson(Object value) {
        if (value instanceof String || value instanceof JSONObject || value instanceof JSONArray) {
            return value;
        }
        return JSON.toJSON(value);
    }

    /**
     * 在消息数组中查找最后一条 user 消息。
     *
     * @param messages 输入消息数组
     * @return 最后一条用户文本
     */
    private String findLastUserMessage(JSONArray messages) {
        String lastUserText = null;
        for (Object item : messages) {
            Object normalizedItem = normalizeJson(item);
            if (normalizedItem instanceof JSONObject message
                    && "user".equals(message.getString("role"))) {
                lastUserText = extractContent(message.get("content"));
            }
        }
        return lastUserText;
    }

    /**
     * 从单条消息对象中解析 user 文本。
     *
     * @param message 输入消息
     * @return 用户文本
     */
    private String extractUserMessage(JSONObject message) {
        if (!"user".equals(message.getString("role"))) {
            return null;
        }
        return extractContent(message.get("content"));
    }

    /**
     * 解析字符串或 OpenAI 内容部件数组。
     *
     * @param content 消息内容
     * @return 文本内容
     */
    private String extractContent(Object content) {
        Object normalizedContent = normalizeJson(content);
        if (normalizedContent instanceof String text) {
            return text;
        }
        if (!(normalizedContent instanceof JSONArray parts)) {
            return null;
        }
        StringBuilder textBuilder = new StringBuilder();
        for (Object part : parts) {
            appendTextPart(textBuilder, normalizeJson(part));
        }
        return textBuilder.toString();
    }

    /**
     * 追加协议允许的文本内容部件，忽略图片等非文本输入。
     *
     * @param textBuilder 文本缓冲区
     * @param part 内容部件
     */
    private void appendTextPart(StringBuilder textBuilder, Object part) {
        if (part instanceof String text) {
            textBuilder.append(text);
            return;
        }
        if (!(part instanceof JSONObject contentPart)) {
            return;
        }
        String type = contentPart.getString("type");
        if ("input_text".equals(type) || "text".equals(type)) {
            String text = contentPart.getString("text");
            if (text != null) {
                textBuilder.append(text);
            }
        }
    }

    /**
     * 从允许的元数据键解析标识，未提供时由服务端生成。
     *
     * @param keys 允许的元数据键
     * @param generatedPrefix 服务端生成前缀
     * @return 校验后的标识
     */
    private String resolveMetadataIdentifier(List<String> keys, String generatedPrefix) {
        if (metadata == null || metadata.isEmpty()) {
            return generateIdentifier(generatedPrefix);
        }
        for (String key : keys) {
            if (!metadata.containsKey(key)) {
                continue;
            }
            Object value = metadata.get(key);
            if (!(value instanceof String identifier)) {
                throw new InvalidOpenAiRequestException(key + "必须是字符串");
            }
            return validateIdentifier(identifier, key);
        }
        return generateIdentifier(generatedPrefix);
    }

    /**
     * 校验会话或任务标识。
     *
     * @param identifier 待校验标识
     * @param fieldName 字段名称
     * @return 规范化标识
     */
    private String validateIdentifier(String identifier, String fieldName) {
        String normalizedIdentifier = identifier.trim();
        if (normalizedIdentifier.isEmpty()
                || normalizedIdentifier.length() > MAX_IDENTIFIER_CHARS
                || !IDENTIFIER_PATTERN.matcher(normalizedIdentifier).matches()) {
            throw new InvalidOpenAiRequestException(fieldName + "格式不合法");
        }
        return normalizedIdentifier;
    }

    /**
     * 生成协议边界内部标识。
     *
     * @param prefix 标识前缀
     * @return 唯一标识
     */
    private String generateIdentifier(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
