package com.example.chat.web.vo;

import com.example.chat.common.dto.ChatMessage;
import com.example.chat.common.dto.ChatRequest;
import com.example.chat.common.enums.ExecutionMode;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 接收 Web 接口请求的 ChatRequestVO 视图对象
 *
 * @author Antigravity
 * @since 2026-07-17
 */
@Data
public class ChatRequestVO {

    private String taskId;

    @NotBlank(message = "sessionId不能为空")
    private String sessionId;

    @NotBlank(message = "userId不能为空")
    private String userId;

    @NotBlank(message = "提问内容question不能为空")
    private String question;

    private List<MessageVO> history;

    // 额外的页面数据属性
    private String pageData;

    /**
     * 旧版模式标识，1=极速工作流，2=Agent
     *
     * @deprecated 请使用 {@link #executionMode} 字段
     */
    @Deprecated
    private Integer mode;

    /**
     * 执行模式：WORKFLOW 或 AGENT
     */
    private ExecutionMode executionMode;

    private String smartBodyCode;

    /**
     * 解析最终的执行模式，默认返回 WORKFLOW
     *
     * @return 解析后的 ExecutionMode
     */
    public ExecutionMode resolveExecutionMode() {
        if (executionMode != null) {
            return executionMode;
        }
        if (mode != null) {
            if (mode.equals(1)) {
                return ExecutionMode.WORKFLOW;
            } else if (mode.equals(2)) {
                return ExecutionMode.AGENT;
            } else {
                throw new IllegalArgumentException("未知的请求模式: mode=" + mode);
            }
        }
        return ExecutionMode.WORKFLOW;
    }

    /**
     * 将外部 VO 对象转换为业务层核心指令 ChatRequest
     */
    public ChatRequest toCommand() {
        // 转换历史纪录
        List<ChatMessage> commandHistory = new ArrayList<>();
        if (history != null) {
            for (MessageVO vo : history) {
                if (vo != null) {
                    commandHistory.add(new ChatMessage(vo.getRole(), vo.getContent()));
                }
            }
        }

        ExecutionMode resolvedMode = resolveExecutionMode();

        // 打包扩展业务属性
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("pageData", pageData != null ? pageData : "");
        attributes.put("smartBodyCode", smartBodyCode != null ? smartBodyCode : "");

        // 默认使用 sessionId + 时间戳或者 UUID 产生唯一的 taskId（若前端不传）
        String finalTaskId = StringUtils.hasText(taskId) ? taskId : java.util.UUID.randomUUID().toString();

        return new ChatRequest(
                finalTaskId,
                sessionId,
                userId,
                question,
                commandHistory,
                attributes,
                resolvedMode
        );
    }

    /**
     * 内部单条消息传输 VO
     */
    @Data
    public static class MessageVO {
        private String role;
        private String content;
    }

    private static class StringUtils {
        public static boolean hasText(String str) {
            return str != null && !str.trim().isEmpty();
        }
    }
}

