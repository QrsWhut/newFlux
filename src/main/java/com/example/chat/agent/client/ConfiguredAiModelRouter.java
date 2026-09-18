package com.example.chat.agent.client;

import com.example.chat.agent.model.AgentModelEvent;
import com.example.chat.agent.model.AgentModelRequest;
import com.example.chat.config.AiRoutingProperties;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于配置的多供应商 AI 模型路由器。
 *
 * @author Codex
 * @since 2026-08-25
 */
public class ConfiguredAiModelRouter implements AiModelRouter {

    /** 路由配置。 */
    private final AiRoutingProperties routingProperties;

    /** 按供应商标识索引的客户端。 */
    private final Map<String, OpenAiResponsesClient> providerClients;

    /**
     * 创建配置路由器。
     *
     * @param routingProperties 路由配置
     * @param clients 供应商客户端
     */
    public ConfiguredAiModelRouter(
            AiRoutingProperties routingProperties,
            List<OpenAiResponsesClient> clients) {
        if (clients == null || clients.isEmpty()) {
            throw new IllegalArgumentException("至少需要配置一个模型供应商");
        }
        this.routingProperties = routingProperties;
        this.providerClients = new LinkedHashMap<>(Math.max(clients.size(), 1));
        for (OpenAiResponsesClient client : clients) {
            OpenAiResponsesClient previous = providerClients.put(client.getProviderId(), client);
            if (previous != null) {
                throw new IllegalArgumentException("模型供应商标识重复：" + client.getProviderId());
            }
        }
    }

    /**
     * 根据显式供应商或默认供应商执行模型请求。
     *
     * @param request 模型请求
     * @return 类型化模型事件流
     */
    @Override
    public Flux<AgentModelEvent> route(AgentModelRequest request) {
        return Flux.defer(() -> {
            if (request == null) {
                return Flux.error(new IllegalArgumentException("模型请求不能为空"));
            }
            String providerId = resolveProviderId(request.getProvider());
            OpenAiResponsesClient client = providerClients.get(providerId);
            if (client == null) {
                return Flux.error(new IllegalArgumentException("未配置模型供应商：" + providerId));
            }
            AgentModelRequest routedRequest = request.toBuilder()
                    .provider(providerId)
                    .model(resolveModel(request.getModel(), client.getDefaultModel()))
                    .build();
            return client.respond(routedRequest);
        });
    }

    private String resolveProviderId(String requestedProvider) {
        if (StringUtils.hasText(requestedProvider)) {
            return requestedProvider;
        }
        if (routingProperties != null && StringUtils.hasText(routingProperties.getDefaultProvider())) {
            return routingProperties.getDefaultProvider();
        }
        if (providerClients.size() == 1) {
            return providerClients.keySet().iterator().next();
        }
        throw new IllegalStateException("存在多个模型供应商时必须配置默认供应商");
    }

    private String resolveModel(String requestedModel, String defaultModel) {
        if (StringUtils.hasText(requestedModel)) {
            return requestedModel;
        }
        if (StringUtils.hasText(defaultModel)) {
            return defaultModel;
        }
        throw new IllegalStateException("模型标识不能为空");
    }
}
