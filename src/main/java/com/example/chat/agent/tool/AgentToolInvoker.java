package com.example.chat.agent.tool;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.example.chat.agent.model.AgentToolDefinition;
import com.example.chat.config.AgentProperties;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Agent 工具统一调用器。
 */
@Component
public class AgentToolInvoker {

    /** 工具注册表。 */
    private final AgentToolRegistry toolRegistry;
    /** 权限服务。 */
    private final AgentToolPermissionService permissionService;
    /** 审计服务。 */
    private final AgentToolAuditService auditService;
    /** Agent 配置。 */
    private final AgentProperties agentProperties;
    /** Bean Validation 校验器。 */
    private final Validator validator;

    /**
     * 创建统一工具调用器。
     *
     * @param toolRegistry 工具注册表
     * @param permissionService 权限服务
     * @param auditService 审计服务
     * @param agentProperties Agent 配置
     * @param validator 参数校验器
     */
    public AgentToolInvoker(AgentToolRegistry toolRegistry,
            AgentToolPermissionService permissionService,
            AgentToolAuditService auditService,
            AgentProperties agentProperties,
            Validator validator) {
        this.toolRegistry = toolRegistry;
        this.permissionService = permissionService;
        this.auditService = auditService;
        this.agentProperties = agentProperties;
        this.validator = validator;
    }

    /**
     * 获取当前调用上下文有权使用的工具定义。
     *
     * @param context 调用上下文
     * @return 可用工具定义
     */
    public List<AgentToolDefinition> getAllowedDefinitions(AgentToolContext context) {
        return toolRegistry.getTools().stream()
                .filter(tool -> permissionService.isAllowed(tool, context))
                .map(AgentTool::definition)
                .toList();
    }

    /**
     * 按名称调用工具。
     *
     * @param toolName 工具名称
     * @param argumentsJson 模型生成的参数 JSON
     * @param context 调用上下文
     * @return 统一工具结果
     */
    public reactor.core.publisher.Mono<AgentToolResult> call(
            String toolName, String argumentsJson, AgentToolContext context) {
        return reactor.core.publisher.Mono.defer(() -> {
            Optional<AgentTool<?>> toolOptional = toolRegistry.getTool(toolName);
            if (toolOptional.isEmpty()) {
                return immediateFailure(toolName, context, AgentToolError.of(
                        AgentToolErrorCode.TOOL_NOT_FOUND, "未知工具: " + toolName, false));
            }
            AgentTool<?> tool = toolOptional.get();
            if (!permissionService.isAllowed(tool, context)) {
                return immediateFailure(toolName, context, AgentToolError.of(
                        AgentToolErrorCode.TOOL_FORBIDDEN, "当前用户无权调用该工具", false));
            }
            return deserializeAndCall(toolName, argumentsJson, tool, context);
        });
    }

    private <I> reactor.core.publisher.Mono<AgentToolResult> deserializeAndCall(
            String toolName, String argumentsJson, AgentTool<I> tool, AgentToolContext context) {
        I input;
        try {
            JSONObject rawInput = JSON.parseObject(argumentsJson);
            if (rawInput == null) {
                return immediateFailure(toolName, context, AgentToolError.of(
                        AgentToolErrorCode.INVALID_ARGUMENTS, "工具参数不能为空", false));
            }
            Set<String> allowedFields = java.util.Arrays.stream(
                            tool.inputType().getDeclaredFields())
                    .filter(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                    .map(java.lang.reflect.Field::getName)
                    .collect(Collectors.toSet());
            List<String> unknownFields = rawInput.keySet().stream()
                    .filter(fieldName -> !allowedFields.contains(fieldName))
                    .sorted()
                    .toList();
            if (!unknownFields.isEmpty()) {
                return immediateFailure(toolName, context, AgentToolError.of(
                        AgentToolErrorCode.INVALID_ARGUMENTS,
                        "存在未定义参数: " + String.join(", ", unknownFields), false));
            }
            input = rawInput.toJavaObject(tool.inputType());
        } catch (JSONException ex) {
            return immediateFailure(toolName, context, AgentToolError.of(
                    AgentToolErrorCode.INVALID_ARGUMENTS, "工具参数不是合法 JSON", false));
        }
        String validationMessage = validator.validate(input).stream()
                .map(this::formatViolation)
                .sorted()
                .collect(Collectors.joining("; "));
        if (!validationMessage.isEmpty()) {
            return immediateFailure(toolName, context, AgentToolError.of(
                    AgentToolErrorCode.VALIDATION_FAILED, validationMessage, false));
        }
        return invokeValidated(toolName, input, tool, context);
    }

    private <I> reactor.core.publisher.Mono<AgentToolResult> invokeValidated(
            String toolName, I input, AgentTool<I> tool, AgentToolContext context) {
        long startNanos = System.nanoTime();
        return tool.call(input, context)
                .switchIfEmpty(reactor.core.publisher.Mono.just(AgentToolResult.failure(
                        AgentToolError.of(AgentToolErrorCode.EMPTY_RESULT,
                                "工具未返回有效结果", true))))
                .timeout(agentProperties.singleCallTimeout())
                .onErrorResume(TimeoutException.class, ex -> reactor.core.publisher.Mono.just(
                        AgentToolResult.failure(AgentToolError.of(
                                AgentToolErrorCode.TOOL_TIMEOUT, "工具调用超时", true))))
                .doOnNext(result -> audit(toolName, tool, context, result, startNanos));
    }

    private String formatViolation(ConstraintViolation<?> violation) {
        return violation.getPropertyPath() + ": " + violation.getMessage();
    }

    private reactor.core.publisher.Mono<AgentToolResult> immediateFailure(
            String toolName, AgentToolContext context, AgentToolError error) {
        AgentToolResult result = AgentToolResult.failure(error);
        auditService.record(toolName, context, result, 0L);
        return reactor.core.publisher.Mono.just(result);
    }

    private void audit(String toolName, AgentTool<?> tool, AgentToolContext context,
            AgentToolResult result, long startNanos) {
        if (tool.metadata().isAuditEnabled()) {
            long durationMillis = (System.nanoTime() - startNanos) / 1_000_000L;
            auditService.record(toolName, context, result, durationMillis);
        }
    }
}
