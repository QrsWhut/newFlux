package com.example.chat.agent.prompt;

import com.example.chat.agent.memory.ConversationMemory;
import com.example.chat.agent.memory.ConversationTurn;
import com.example.chat.agent.model.AgentMessage;
import com.example.chat.common.dto.ChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 系统提示词与多轮上下文消息组装器
 *
 * @author Antigravity
 * @since 2026-08-12
 */
@Slf4j
@Component
public class AgentPromptFactory {

    public static final String PROMPT_VERSION = "v1.0.0-20260812";

    public static final String SYSTEM_PROMPT = """
你是金融对话助手。基于会话上下文理解指代，不执行固定问句改写。
对实时行情、财务指标使用 queryFinancialData；对新闻和研报使用 searchFinancialDocuments；
对象无法确定时优先 resolveFinancialEntity。若关键信息仍不足，直接简洁地询问用户，
不要猜测、不要调用无意义工具；该澄清文本就是本轮最终回答。
不得编造工具结果；工具失败时说明限制并给出下一步建议。
""";

    /**
     * 组装包含系统策略、页面上下文、历史摘要、最近三轮问答和当前提问的消息列表
     *
     * @param request 对话请求 DTO
     * @param memory  服务端维持的会话记忆
     * @return 准备传递给大模型的 AgentMessage 列表
     */
    public List<AgentMessage> buildInitialMessages(ChatRequest request, ConversationMemory memory) {
        List<AgentMessage> messages = new ArrayList<>();

        // 1. 系统策略
        messages.add(AgentMessage.system(SYSTEM_PROMPT.trim()));

        // 2. 页面数据 (若存在)
        if (request.attributes() != null) {
            Object pageDataObj = request.attributes().get("pageData");
            if (pageDataObj instanceof String pageData && !pageData.trim().isEmpty()) {
                String truncatedPageData = pageData.length() > 2000 ? pageData.substring(0, 2000) + "...[截断]" : pageData;
                messages.add(AgentMessage.system("当前页面数据上下文:\n" + truncatedPageData));
            }
        }

        // 3. 历史摘要 (若存在)
        if (memory != null && memory.getSummary() != null && !memory.getSummary().trim().isEmpty()) {
            messages.add(AgentMessage.system("较早轮次会话摘要:\n" + memory.getSummary().trim()));
        }

        // 4. 最近三轮完整问答
        if (memory != null && memory.getRecentTurns() != null) {
            for (ConversationTurn turn : memory.getRecentTurns()) {
                if (turn.getUserQuestion() != null && !turn.getUserQuestion().isEmpty()) {
                    messages.add(AgentMessage.user(turn.getUserQuestion()));
                }
                if (turn.getAssistantAnswer() != null && !turn.getAssistantAnswer().isEmpty()) {
                    messages.add(AgentMessage.assistant(turn.getAssistantAnswer()));
                }
            }
        }

        // 5. 当前轮用户提问
        messages.add(AgentMessage.user(request.question()));

        log.info("AgentPromptFactory 构建完成初始消息, version={}, messageCount={}", PROMPT_VERSION, messages.size());
        return messages;
    }
}
