package com.example.chat.web.controller;

import com.example.chat.common.dto.ChatEvent;
import com.example.chat.common.dto.ChatRequest;
import com.example.chat.common.security.RequestIdentity;
import com.example.chat.common.security.RequestIdentityResolver;
import com.example.chat.service.interf.ChatService;
import com.example.chat.task.TaskCancellationService;
import com.example.chat.web.vo.ChatRequestVO;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 旧版对话接口受信身份与取消边界测试。
 */
class ChatControllerTest {

    /** 测试身份请求头。 */
    private static final String TEST_USER_HEADER = "X-Test-User";

    /**
     * 验证流式接口忽略请求体用户标识，只传递受信身份。
     */
    @Test
    void shouldUseTrustedIdentityForStream() {
        AtomicReference<ChatRequest> capturedRequest = new AtomicReference<>();
        ChatService chatService = request -> {
            capturedRequest.set(request);
            return Flux.just(ChatEvent.complete(request.taskId(), 1L));
        };
        ChatController controller = controller(chatService, new TaskCancellationService());
        ChatRequestVO requestVO = request();
        requestVO.setUserId("forged-user");

        StepVerifier.create(controller.stream(
                        requestVO,
                        request("trusted-user")))
                .assertNext(this::assertCompleteEvent)
                .verifyComplete();

        assertEquals("trusted-user", capturedRequest.get().userId());
    }

    /**
     * 验证取消接口不能跨用户取消相同任务标识。
     */
    @Test
    void shouldScopeCancellationByTrustedIdentity() {
        TaskCancellationService cancellationService = new TaskCancellationService();
        AtomicBoolean ownerDisposed = new AtomicBoolean();
        cancellationService.register(
                "owner-user",
                "shared-task",
                () -> ownerDisposed.set(true));
        ChatController controller = controller(request -> Flux.empty(), cancellationService);

        String attackerResult = controller.cancel(
                "shared-task",
                request("attacker-user"));
        String ownerResult = controller.cancel(
                "shared-task",
                request("owner-user"));

        assertEquals("NOT_FOUND", attackerResult);
        assertFalse(ownerDisposed.get() && "SUCCESS".equals(attackerResult));
        assertEquals("SUCCESS", ownerResult);
        assertTrue(ownerDisposed.get());
    }

    /**
     * 创建被测控制器。
     *
     * @param chatService 对话服务
     * @param cancellationService 取消服务
     * @return 被测控制器
     */
    private ChatController controller(
            ChatService chatService,
            TaskCancellationService cancellationService) {
        RequestIdentityResolver identityResolver = request ->
                new RequestIdentity(request.getHeaders().getFirst(TEST_USER_HEADER));
        return new ChatController(
                chatService,
                cancellationService,
                identityResolver);
    }

    /**
     * 创建模拟服务端请求。
     *
     * @param userId 受信测试用户
     * @return 模拟请求
     */
    private MockServerHttpRequest request(String userId) {
        return MockServerHttpRequest.post("/api/chat/stream")
                .header(TEST_USER_HEADER, userId)
                .build();
    }

    /**
     * 创建基础对话请求。
     *
     * @return 请求视图对象
     */
    private ChatRequestVO request() {
        ChatRequestVO requestVO = new ChatRequestVO();
        requestVO.setTaskId("task-001");
        requestVO.setSessionId("session-001");
        requestVO.setQuestion("测试问题");
        return requestVO;
    }

    /**
     * 校验完成事件。
     *
     * @param serverSentEvent SSE 事件
     */
    private void assertCompleteEvent(ServerSentEvent<ChatEvent> serverSentEvent) {
        assertEquals("COMPLETE", serverSentEvent.event());
        assertEquals("task-001", serverSentEvent.data().taskId());
    }
}