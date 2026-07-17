package com.example.chat.web.vo;

import com.example.chat.common.dto.ChatMessage;
import com.example.chat.common.dto.ChatRequest;
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

    // 执行模式，默认 1=极速
    private int mode = 1;

    private String smartBodyCode;

    /**
     * 将外部 VO 对象转换为业务层核心指令 ChatRequest
     */
    public ChatRequest toCommand() {
        // 转换历史纪录
        List<ChatMessage> commandHistory = new ArrayList<>();
        if (history != null) {
            for (MessageVO vo : history) {
                commandHistory.add(new ChatMessage(vo.getRole(), vo.getContent()));
            }
        }

        // 打包扩展业务属性
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("pageData", pageData != null ? pageData : "");
        attributes.put("mode", mode);
        attributes.put("smartBodyCode", smartBodyCode != null ? smartBodyCode : "");

        // 默认使用 sessionId + 时间戳或者 UUID 产生唯一的 taskId（若前端不传）
        String finalTaskId = StringUtils.hasText(taskId) ? taskId : java.util.UUID.randomUUID().toString();

        return new ChatRequest(
                finalTaskId,
                sessionId,
                userId,
                question,
                commandHistory,
                attributes
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
