package com.example.chat.common.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * OpenAI Responses 协议 JSON 负载工厂。
 *
 * <p>警告：外部协议强制使用 created_at、output_text、output_index 等 snake_case 字段，无法改为项目内部
 * lowerCamelCase 风格。正确做法是在本边界适配器内隔离这些固定字段，内部 DTO 继续使用 lowerCamelCase。</p>
 */
public final class OpenAiResponseJsonFactory {

    /** Responses 对象类型。 */
    private static final String RESPONSE_OBJECT = "response";
    /** 输出消息对象类型。 */
    private static final String MESSAGE_OBJECT = "message";
    /** 输出文本内容类型。 */
    private static final String OUTPUT_TEXT_TYPE = "output_text";
    /** 响应创建事件类型。 */
    private static final String RESPONSE_CREATED_TYPE = "response.created";
    /** 文本增量事件类型。 */
    private static final String RESPONSE_DELTA_TYPE = "response.output_text.delta";
    /** 响应完成事件类型。 */
    private static final String RESPONSE_COMPLETED_TYPE = "response.completed";
    /** 错误事件类型。 */
    private static final String ERROR_TYPE = "error";
    /** 处理中状态。 */
    private static final String IN_PROGRESS_STATUS = "in_progress";
    /** 完成状态。 */
    private static final String COMPLETED_STATUS = "completed";
    /** 助手角色。 */
    private static final String ASSISTANT_ROLE = "assistant";
    /** 单输出项索引。 */
    private static final int OUTPUT_INDEX = 0;
    /** 单内容项索引。 */
    private static final int CONTENT_INDEX = 0;
    /** 初始事件序号。 */
    private static final long CREATED_SEQUENCE_NUMBER = 0L;

    /**
     * 阻止实例化协议工厂。
     */
    private OpenAiResponseJsonFactory() {
    }

    /**
     * 构建 response.created 事件负载。
     *
     * @param responseId 响应标识
     * @param taskId 任务标识
     * @param sessionId 会话标识
     * @param model 模型标识
     * @param createdAtEpochSeconds 创建秒级时间戳
     * @return 事件 JSON
     */
    public static JSONObject createdEvent(
            String responseId,
            String taskId,
            String sessionId,
            String model,
            long createdAtEpochSeconds) {
        JSONObject event = new JSONObject(true);
        event.put("type", RESPONSE_CREATED_TYPE);
        event.put("sequence_number", CREATED_SEQUENCE_NUMBER);
        event.put("response", baseResponse(
                responseId,
                taskId,
                sessionId,
                model,
                createdAtEpochSeconds,
                IN_PROGRESS_STATUS,
                new JSONArray()));
        return event;
    }

    /**
     * 构建 response.output_text.delta 事件负载。
     *
     * @param messageId 输出消息标识
     * @param sequenceNumber 事件序号
     * @param delta 文本增量
     * @return 事件 JSON
     */
    public static JSONObject textDeltaEvent(String messageId, long sequenceNumber, String delta) {
        JSONObject event = new JSONObject(true);
        event.put("type", RESPONSE_DELTA_TYPE);
        event.put("sequence_number", sequenceNumber);
        event.put("item_id", messageId);
        event.put("output_index", OUTPUT_INDEX);
        event.put("content_index", CONTENT_INDEX);
        event.put("delta", delta);
        return event;
    }

    /**
     * 构建 response.completed 事件负载。
     *
     * @param responseId 响应标识
     * @param messageId 输出消息标识
     * @param taskId 任务标识
     * @param sessionId 会话标识
     * @param model 模型标识
     * @param createdAtEpochSeconds 创建秒级时间戳
     * @param sequenceNumber 事件序号
     * @param answer 完整回答
     * @return 事件 JSON
     */
    public static JSONObject completedEvent(
            String responseId,
            String messageId,
            String taskId,
            String sessionId,
            String model,
            long createdAtEpochSeconds,
            long sequenceNumber,
            String answer) {
        JSONObject event = new JSONObject(true);
        event.put("type", RESPONSE_COMPLETED_TYPE);
        event.put("sequence_number", sequenceNumber);
        event.put("response", completedResponse(
                responseId,
                messageId,
                taskId,
                sessionId,
                model,
                createdAtEpochSeconds,
                answer));
        return event;
    }

    /**
     * 构建非流式完整 Responses 对象。
     *
     * @param responseId 响应标识
     * @param messageId 输出消息标识
     * @param taskId 任务标识
     * @param sessionId 会话标识
     * @param model 模型标识
     * @param createdAtEpochSeconds 创建秒级时间戳
     * @param answer 完整回答
     * @return Responses JSON
     */
    public static JSONObject completedResponse(
            String responseId,
            String messageId,
            String taskId,
            String sessionId,
            String model,
            long createdAtEpochSeconds,
            String answer) {
        JSONArray output = new JSONArray(1);
        output.add(outputMessage(messageId, answer));
        return baseResponse(
                responseId,
                taskId,
                sessionId,
                model,
                createdAtEpochSeconds,
                COMPLETED_STATUS,
                output);
    }

    /**
     * 构建流式错误事件负载。
     *
     * @param sequenceNumber 事件序号
     * @param errorCode 错误码
     * @param errorMessage 错误说明
     * @return 错误事件 JSON
     */
    public static JSONObject errorEvent(long sequenceNumber, String errorCode, String errorMessage) {
        JSONObject event = new JSONObject(true);
        event.put("type", ERROR_TYPE);
        event.put("sequence_number", sequenceNumber);
        event.put("error", errorObject(errorCode, errorMessage));
        return event;
    }

    /**
     * 构建非流式 OpenAI 错误信封。
     *
     * @param errorCode 错误码
     * @param errorMessage 错误说明
     * @return 错误信封 JSON
     */
    public static JSONObject errorEnvelope(String errorCode, String errorMessage) {
        JSONObject envelope = new JSONObject(true);
        envelope.put("error", errorObject(errorCode, errorMessage));
        return envelope;
    }

    /**
     * 构建 Responses 基础对象。
     *
     * @param responseId 响应标识
     * @param taskId 任务标识
     * @param sessionId 会话标识
     * @param model 模型标识
     * @param createdAtEpochSeconds 创建秒级时间戳
     * @param status 响应状态
     * @param output 输出列表
     * @return Responses 对象
     */
    private static JSONObject baseResponse(
            String responseId,
            String taskId,
            String sessionId,
            String model,
            long createdAtEpochSeconds,
            String status,
            JSONArray output) {
        JSONObject response = new JSONObject(true);
        response.put("id", responseId);
        response.put("object", RESPONSE_OBJECT);
        response.put("created_at", createdAtEpochSeconds);
        response.put("status", status);
        response.put("model", model);
        response.put("output", output);
        response.put("metadata", responseMetadata(taskId, sessionId));
        return response;
    }

    /**
     * 构建助手输出消息。
     *
     * @param messageId 消息标识
     * @param answer 完整回答
     * @return 输出消息
     */
    private static JSONObject outputMessage(String messageId, String answer) {
        JSONObject outputText = new JSONObject(true);
        outputText.put("type", OUTPUT_TEXT_TYPE);
        outputText.put("text", answer);
        outputText.put("annotations", new JSONArray());

        JSONArray content = new JSONArray(1);
        content.add(outputText);
        JSONObject message = new JSONObject(true);
        message.put("id", messageId);
        message.put("type", MESSAGE_OBJECT);
        message.put("status", COMPLETED_STATUS);
        message.put("role", ASSISTANT_ROLE);
        message.put("content", content);
        return message;
    }

    /**
     * 构建服务端会话元数据。
     *
     * @param taskId 任务标识
     * @param sessionId 会话标识
     * @return 元数据对象
     */
    private static JSONObject responseMetadata(String taskId, String sessionId) {
        JSONObject metadata = new JSONObject(true);
        metadata.put("taskId", taskId);
        metadata.put("conversationId", sessionId);
        return metadata;
    }

    /**
     * 构建协议错误对象。
     *
     * @param errorCode 错误码
     * @param errorMessage 错误说明
     * @return 错误对象
     */
    private static JSONObject errorObject(String errorCode, String errorMessage) {
        JSONObject error = new JSONObject(true);
        error.put("message", errorMessage);
        error.put("type", "agent_error");
        error.put("code", errorCode);
        error.put("param", null);
        return error;
    }
}
