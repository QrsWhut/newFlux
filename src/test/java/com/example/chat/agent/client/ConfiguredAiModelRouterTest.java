package com.example.chat.agent.client;

import com.example.chat.agent.model.AgentModelEvent;
import com.example.chat.agent.model.AgentModelRequest;
import com.example.chat.config.AiRoutingProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 配置化 AI 模型路由器测试。
 *
 * @author Codex
 * @since 2026-08-25
 */
public class ConfiguredAiModelRouterTest {

    /**
     * 验证默认供应商及默认模型选择。
     */
    @Test
    public void testDefaultProviderAndModel() {
        OpenAiResponsesClient openAiClient = createClient("openai", "gpt-default");
        OpenAiResponsesClient otherClient = createClient("other", "other-default");
        AiRoutingProperties properties = new AiRoutingProperties();
        properties.setDefaultProvider("openai");
        ConfiguredAiModelRouter router = new ConfiguredAiModelRouter(
                properties,
                List.of(openAiClient, otherClient));
        AgentModelRequest request = AgentModelRequest.builder().build();

        StepVerifier.create(router.route(request))
                .expectNextMatches(event ->
                        event.getType() == AgentModelEvent.EventType.COMPLETED)
                .verifyComplete();

        ArgumentCaptor<AgentModelRequest> captor = ArgumentCaptor.forClass(AgentModelRequest.class);
        verify(openAiClient).respond(captor.capture());
        assertEquals("openai", captor.getValue().getProvider());
        assertEquals("gpt-default", captor.getValue().getModel());
    }

    /**
     * 验证请求级供应商及模型覆盖。
     */
    @Test
    public void testRequestOverridesProviderAndModel() {
        OpenAiResponsesClient openAiClient = createClient("openai", "gpt-default");
        OpenAiResponsesClient otherClient = createClient("other", "other-default");
        AiRoutingProperties properties = new AiRoutingProperties();
        properties.setDefaultProvider("openai");
        ConfiguredAiModelRouter router = new ConfiguredAiModelRouter(
                properties,
                List.of(openAiClient, otherClient));
        AgentModelRequest request = AgentModelRequest.builder()
                .provider("other")
                .model("other-explicit")
                .build();

        StepVerifier.create(router.route(request))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<AgentModelRequest> captor = ArgumentCaptor.forClass(AgentModelRequest.class);
        verify(otherClient).respond(captor.capture());
        assertEquals("other", captor.getValue().getProvider());
        assertEquals("other-explicit", captor.getValue().getModel());
    }

    /**
     * 验证未知供应商以响应式错误返回。
     */
    @Test
    public void testUnknownProvider() {
        OpenAiResponsesClient openAiClient = createClient("openai", "gpt-default");
        ConfiguredAiModelRouter router = new ConfiguredAiModelRouter(
                new AiRoutingProperties(),
                List.of(openAiClient));
        AgentModelRequest request = AgentModelRequest.builder()
                .provider("missing")
                .build();

        StepVerifier.create(router.route(request))
                .expectErrorMatches(throwable ->
                        throwable instanceof IllegalArgumentException
                                && throwable.getMessage().contains("missing"))
                .verify();
    }

    private OpenAiResponsesClient createClient(String providerId, String model) {
        OpenAiResponsesClient client = mock(OpenAiResponsesClient.class);
        when(client.getProviderId()).thenReturn(providerId);
        when(client.getDefaultModel()).thenReturn(model);
        when(client.respond(any())).thenReturn(Flux.just(AgentModelEvent.completed("response")));
        return client;
    }
}
