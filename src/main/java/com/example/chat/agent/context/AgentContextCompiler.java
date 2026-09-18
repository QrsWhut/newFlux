package com.example.chat.agent.context;

import com.alibaba.fastjson.JSON;
import com.example.chat.agent.memory.ConversationTurnReplayService;
import com.example.chat.agent.model.AgentMessage;
import com.example.chat.agent.model.AgentToolDefinition;
import com.example.chat.agent.prompt.PromptCatalog;
import com.example.chat.agent.prompt.PromptPurpose;
import com.example.chat.agent.prompt.PromptSnapshot;
import com.example.chat.common.dto.ChatRequest;
import com.example.chat.common.dto.agent.memory.ConversationSnapshotDTO;
import com.example.chat.common.dto.agent.memory.ConversationSummaryDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 将会话快照、版本化提示词、当前请求和冻结工具编译成单次模型输入。
 */
@Component
public class AgentContextCompiler {

    /** 不可信数据标识。 */
    private static final String UNTRUSTED_DATA_MARKER = "untrusted-data";

    /** 提示词目录。 */
    private final PromptCatalog promptCatalog;
    /** Turn 确定性回放服务。 */
    private final ConversationTurnReplayService replayService;

    /**
     * 创建 Agent 上下文编译器。
     *
     * @param promptCatalog 提示词目录
     * @param replayService Turn 回放服务
     */
    public AgentContextCompiler(
            PromptCatalog promptCatalog,
            ConversationTurnReplayService replayService) {
        this.promptCatalog = promptCatalog;
        this.replayService = replayService;
    }

    /**
     * 在路由前编译上下文，供应方和模型字段保持为空。
     *
     * @param snapshot 会话快照，首轮允许为空
     * @param request 当前请求
     * @param frozenTools 本步骤冻结工具定义
     * @return 不可变已准备上下文
     */
    public PreparedAgentContext compile(
            ConversationSnapshotDTO snapshot,
            ChatRequest request,
            List<AgentToolDefinition> frozenTools) {
        return compile(snapshot, request, frozenTools, "", "");
    }

    /**
     * 使用路由结果编译上下文。
     *
     * @param snapshot 会话快照，首轮允许为空
     * @param request 当前请求
     * @param frozenTools 本步骤冻结工具定义
     * @param providerCode 模型供应方编码
     * @param modelName 模型名称
     * @return 不可变已准备上下文
     */
    public PreparedAgentContext compile(
            ConversationSnapshotDTO snapshot,
            ChatRequest request,
            List<AgentToolDefinition> frozenTools,
            String providerCode,
            String modelName) {
        validateRequest(request);
        PromptSnapshot promptSnapshot = promptCatalog.getPrompt(PromptPurpose.FINANCIAL_AGENT_SYSTEM);
        List<AgentToolDefinition> orderedTools = orderTools(frozenTools);
        List<AgentMessage> historicalMessages = snapshot == null
                ? List.of() : replayService.replay(snapshot.turns());
        ConversationSummaryDTO summary = snapshot == null ? null : snapshot.latestSummary();
        String summaryContent = summary == null ? "" : formatSummary(summary);
        String pageContext = extractPageContext(request.attributes());
        String pageMessageContent = formatPageContext(pageContext);
        String questionMessageContent = formatQuestion(request.question());
        String currentUserMessageContent = pageMessageContent.isEmpty()
                ? questionMessageContent : pageMessageContent + "\n" + questionMessageContent;

        List<AgentMessage> messages = new ArrayList<>();
        messages.add(AgentMessage.developer(promptSnapshot.content()));
        if (!summaryContent.isEmpty()) {
            messages.add(AgentMessage.user(summaryContent));
        }
        messages.addAll(historicalMessages);
        messages.add(AgentMessage.user(currentUserMessageContent));

        List<String> serializedHistoricalMessages = historicalMessages.stream()
                .map(JSON::toJSONString)
                .toList();
        List<String> serializedTools = orderedTools.stream()
                .map(tool -> tool.toResponsesTool().toJSONString())
                .toList();
        String estimatedPageContent = pageMessageContent.isEmpty()
                ? "" : pageMessageContent + "\n";
        ContextEstimationInput estimationInput = new ContextEstimationInput(
                providerCode,
                modelName,
                promptSnapshot.content(),
                serializedTools,
                summaryContent,
                serializedHistoricalMessages,
                questionMessageContent,
                estimatedPageContent,
                List.of(),
                messages.size());
        return new PreparedAgentContext(
                snapshot == null ? null : snapshot.conversationId(),
                snapshot == null ? 1 : snapshot.contextVersion(),
                snapshot == null ? 0 : snapshot.latestSummaryVersion(),
                promptSnapshot,
                messages,
                orderedTools,
                estimationInput);
    }

    private List<AgentToolDefinition> orderTools(List<AgentToolDefinition> frozenTools) {
        if (frozenTools == null || frozenTools.isEmpty()) {
            return List.of();
        }
        return frozenTools.stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(this::toolName))
                .toList();
    }

    private String toolName(AgentToolDefinition tool) {
        return tool.getFunction() == null || tool.getFunction().getName() == null
                ? "" : tool.getFunction().getName();
    }

    private String formatSummary(ConversationSummaryDTO summary) {
        return """
                <historical_summary trust="%s" covered_start_turn_no="%s" covered_end_turn_no="%s">
                %s
                </historical_summary>
                """.formatted(
                UNTRUSTED_DATA_MARKER,
                summary.coveredStartTurnNo(),
                summary.coveredEndTurnNo(),
                escapeXml(summary.summaryContent())).trim();
    }

    private String extractPageContext(Map<String, Object> attributes) {
        if (attributes == null) {
            return "";
        }
        Object pageData = attributes.get("pageData");
        return pageData instanceof String text ? text : "";
    }

    private String formatPageContext(String pageContext) {
        if (pageContext == null || pageContext.isBlank()) {
            return "";
        }
        return "<page_context trust=\"" + UNTRUSTED_DATA_MARKER + "\">"
                + escapeXml(pageContext) + "</page_context>";
    }

    private String formatQuestion(String question) {
        return "<question trust=\"" + UNTRUSTED_DATA_MARKER + "\">"
                + escapeXml(question) + "</question>";
    }

    private void validateRequest(ChatRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("当前对话请求和问题不能为空");
        }
    }

    private String escapeXml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
