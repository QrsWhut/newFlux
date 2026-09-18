package com.example.chat.integration.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.example.chat.common.dto.wind.WindContent;
import com.example.chat.common.dto.wind.WindJsonRpcRequest;
import com.example.chat.common.dto.wind.WindToolCallRequest;
import com.example.chat.common.dto.wind.WindToolCallResult;
import com.example.chat.common.dto.wind.WindToolCatalogResult;
import com.example.chat.common.dto.wind.WindToolDefinition;
import com.example.chat.common.enums.FinancialProvider;
import com.example.chat.common.enums.FinancialProviderErrorCode;
import com.example.chat.common.enums.WindServerType;
import com.example.chat.common.exception.FinancialProviderException;
import com.example.chat.config.WindProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * 基于 WebClient 的 Wind MCP 客户端。
 */
@Component
public class WebClientWindMcpClient implements WindMcpClient {

    /** MCP 协议版本。 */
    private static final String MCP_PROTOCOL_VERSION = "2025-03-26";
    /** JSON-RPC 协议版本。 */
    private static final String JSON_RPC_VERSION = "2.0";
    /** Wind 数据来源说明。 */
    private static final String WIND_SOURCE = "万得 Wind 金融数据服务";
    /** 后端无效值标记。 */
    private static final String INVALID_VALUE = "INVALID";
    /** 声明数量不可靠警告码。 */
    private static final String COUNT_WARNING_CODE = "UNRELIABLE_DECLARED_COUNT";
    /** 无效值归一化警告码。 */
    private static final String INVALID_WARNING_CODE = "BACKEND_INVALID_AS_NULL";

    /** WebClient 构建器。 */
    private final WebClient.Builder webClientBuilder;
    /** Wind 运行配置。 */
    private final WindProperties windProperties;
    /** Wind 凭据提供器。 */
    private final WindCredentialProvider credentialProvider;
    /** 固定服务地址解析器。 */
    private final Function<WindServerType, String> endpointResolver;
    /** JSON-RPC 请求序号。 */
    private final AtomicLong requestSequence = new AtomicLong();

    /**
     * 创建生产环境 Wind MCP 客户端。
     *
     * @param webClientBuilder WebClient 构建器
     * @param windProperties Wind 运行配置
     * @param credentialProvider Wind 凭据提供器
     */
    @Autowired
    public WebClientWindMcpClient(WebClient.Builder webClientBuilder,
            WindProperties windProperties, WindCredentialProvider credentialProvider) {
        this(webClientBuilder, windProperties, credentialProvider, WindServerType::getEndpoint);
    }

    /**
     * 创建可替换服务地址的 Wind MCP 客户端，仅供包内测试使用。
     *
     * @param webClientBuilder WebClient 构建器
     * @param windProperties Wind 运行配置
     * @param credentialProvider Wind 凭据提供器
     * @param endpointResolver 服务地址解析器
     */
    WebClientWindMcpClient(WebClient.Builder webClientBuilder,
            WindProperties windProperties,
            WindCredentialProvider credentialProvider,
            Function<WindServerType, String> endpointResolver) {
        this.webClientBuilder = webClientBuilder;
        this.windProperties = windProperties;
        this.credentialProvider = credentialProvider;
        this.endpointResolver = endpointResolver;
    }

    @Override
    public Mono<WindToolCatalogResult> listTools(WindServerType serverType) {
        return Mono.defer(() -> {
            validateServerType(serverType);
            String apiKey = requireCredential();
            return initialize(serverType, apiKey)
                    .then(executeRpc(
                            serverType,
                            apiKey,
                            createRequest("tools/list", new JSONObject(true)),
                            windProperties.resolveCallTimeout()))
                    .map(result -> toCatalogResult(serverType, result));
        });
    }

    @Override
    public Mono<WindToolCallResult> call(WindToolCallRequest request) {
        return Mono.defer(() -> {
            validateCallRequest(request);
            String apiKey = requireCredential();
            JSONObject params = new JSONObject(true);
            params.put("name", request.getToolName());
            params.put("arguments", request.getArguments() == null
                    ? new JSONObject(true) : request.getArguments());
            return initialize(request.getServerType(), apiKey)
                    .then(executeRpc(
                            request.getServerType(),
                            apiKey,
                            createRequest("tools/call", params),
                            windProperties.resolveCallTimeout()))
                    .map(result -> toCallResult(request, result));
        });
    }

    private Mono<Void> initialize(WindServerType serverType, String apiKey) {
        JSONObject clientInfo = new JSONObject(true);
        clientInfo.put("name", "newAB1Workflow");
        clientInfo.put("version", "1.0.0");
        JSONObject params = new JSONObject(true);
        params.put("protocolVersion", MCP_PROTOCOL_VERSION);
        params.put("capabilities", new JSONObject(true));
        params.put("clientInfo", clientInfo);
        WindJsonRpcRequest request = createRequest("initialize", params);
        return executeRpc(serverType, apiKey, request, windProperties.resolveInitializeTimeout()).then();
    }

    private Mono<JSONObject> executeRpc(WindServerType serverType,
            String apiKey, WindJsonRpcRequest request, Duration timeout) {
        String endpoint = endpointResolver.apply(serverType);
        return webClientBuilder.build()
                .post()
                .uri(endpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .bodyValue(request)
                .exchangeToMono(response -> {
                    if (response.statusCode().isError()) {
                        return Mono.error(toHttpException(response.statusCode()));
                    }
                    return response.bodyToMono(String.class)
                            .switchIfEmpty(Mono.error(providerException(
                                    FinancialProviderErrorCode.INVALID_RESPONSE,
                                    "Wind MCP 返回空响应", false)))
                            .map(this::parseEnvelope)
                            .map(this::extractRpcResult);
                })
                .timeout(timeout)
                .onErrorMap(TimeoutException.class, ex -> providerException(
                        FinancialProviderErrorCode.TIMEOUT, "Wind MCP 请求超时", true))
                .onErrorMap(WebClientRequestException.class, ex -> providerException(
                        FinancialProviderErrorCode.NETWORK_ERROR, "Wind MCP 网络请求失败", true));
    }

    private WindJsonRpcRequest createRequest(String method, JSONObject params) {
        return WindJsonRpcRequest.builder()
                .jsonrpc(JSON_RPC_VERSION)
                .id(Long.toString(requestSequence.incrementAndGet()))
                .method(method)
                .params(params)
                .build();
    }

    private JSONObject parseEnvelope(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            throw providerException(
                    FinancialProviderErrorCode.INVALID_RESPONSE,
                    "Wind MCP 返回空响应", false);
        }
        String trimmedBody = responseBody.trim();
        try {
            if (trimmedBody.startsWith("{")) {
                return JSON.parseObject(trimmedBody);
            }
            return parseSseEnvelope(trimmedBody);
        } catch (JSONException ex) {
            throw providerException(
                    FinancialProviderErrorCode.INVALID_RESPONSE,
                    "Wind MCP 响应不是有效 JSON", false);
        }
    }

    private JSONObject parseSseEnvelope(String responseBody) {
        JSONObject latestEnvelope = null;
        StringBuilder dataBuilder = new StringBuilder();
        for (String line : responseBody.lines().toList()) {
            if (line.isBlank()) {
                latestEnvelope = parseSseEvent(dataBuilder, latestEnvelope);
                dataBuilder.setLength(0);
            } else if (line.startsWith("data:")) {
                if (!dataBuilder.isEmpty()) {
                    dataBuilder.append('\n');
                }
                dataBuilder.append(line.substring("data:".length()).trim());
            }
        }
        latestEnvelope = parseSseEvent(dataBuilder, latestEnvelope);
        if (latestEnvelope == null) {
            throw providerException(
                    FinancialProviderErrorCode.INVALID_RESPONSE,
                    "Wind MCP SSE 响应缺少有效数据", false);
        }
        return latestEnvelope;
    }

    private JSONObject parseSseEvent(StringBuilder dataBuilder, JSONObject previousEnvelope) {
        if (dataBuilder.isEmpty() || "[DONE]".contentEquals(dataBuilder)) {
            return previousEnvelope;
        }
        return JSON.parseObject(dataBuilder.toString());
    }

    private JSONObject extractRpcResult(JSONObject envelope) {
        if (envelope == null) {
            throw providerException(
                    FinancialProviderErrorCode.INVALID_RESPONSE,
                    "Wind MCP 响应体为空", false);
        }
        JSONObject error = envelope.getJSONObject("error");
        if (error != null && !error.isEmpty()) {
            throw providerException(classifyError(error.getString("message")));
        }
        JSONObject result = envelope.getJSONObject("result");
        if (result == null) {
            throw providerException(
                    FinancialProviderErrorCode.INVALID_RESPONSE,
                    "Wind MCP 响应缺少结果字段", false);
        }
        return result;
    }

    private WindToolCatalogResult toCatalogResult(WindServerType serverType, JSONObject result) {
        JSONArray tools = result.getJSONArray("tools");
        if (tools == null || tools.isEmpty()) {
            return WindToolCatalogResult.builder()
                    .serverType(serverType)
                    .tools(Collections.emptyList())
                    .build();
        }
        List<WindToolDefinition> definitions = new ArrayList<>(tools.size());
        for (int index = 0; index < tools.size(); index++) {
            JSONObject tool = tools.getJSONObject(index);
            if (tool == null || !serverType.isToolAllowed(tool.getString("name"))) {
                continue;
            }
            definitions.add(WindToolDefinition.builder()
                    .name(tool.getString("name"))
                    .description(tool.getString("description"))
                    .inputSchema(tool.getJSONObject("inputSchema"))
                    .build());
        }
        return WindToolCatalogResult.builder()
                .serverType(serverType)
                .tools(List.copyOf(definitions))
                .build();
    }

    private WindToolCallResult toCallResult(WindToolCallRequest request, JSONObject result) {
        validateToolResult(result);
        NormalizationContext normalizationContext = new NormalizationContext();
        List<WindContent> content = normalizeContent(result.getJSONArray("content"), normalizationContext);
        JSONObject structuredContent = normalizeStructuredContent(
                result.getJSONObject("structuredContent"), normalizationContext);
        List<JSONObject> warnings = createWarnings(normalizationContext);
        return WindToolCallResult.builder()
                .serverType(request.getServerType())
                .toolName(request.getToolName())
                .content(content)
                .structuredContent(structuredContent)
                .warnings(warnings)
                .source(WIND_SOURCE)
                .build();
    }

    private void validateToolResult(JSONObject result) {
        if (result.getBooleanValue("isError")) {
            throw providerException(classifyError(extractContentText(result.getJSONArray("content"))));
        }
        validateBusinessValue(result.get("structuredContent"));
        JSONArray content = result.getJSONArray("content");
        if (content == null) {
            return;
        }
        for (int index = 0; index < content.size(); index++) {
            JSONObject contentItem = content.getJSONObject(index);
            if (contentItem == null || !StringUtils.hasText(contentItem.getString("text"))) {
                continue;
            }
            try {
                validateBusinessValue(JSON.parse(contentItem.getString("text")));
            } catch (JSONException ex) {
                continue;
            }
        }
    }

    private void validateBusinessValue(Object value) {
        if (!(value instanceof JSONObject objectValue)) {
            return;
        }
        Integer errorCode = objectValue.getInteger("mcp_tool_error_code");
        if (errorCode != null && errorCode != 0) {
            throw providerException(classifyError(objectValue.getString("message")));
        }
        Object errorValue = objectValue.get("error");
        if (errorValue != null && StringUtils.hasText(String.valueOf(errorValue))) {
            throw providerException(classifyError(String.valueOf(errorValue)));
        }
        Object nestedData = objectValue.get("data");
        if (nestedData instanceof JSONObject nestedObject) {
            Integer nestedCode = nestedObject.getInteger("code");
            if (nestedCode != null && nestedCode != 0 && nestedCode != 200) {
                throw providerException(classifyError(nestedObject.getString("message")));
            }
        }
    }

    private List<WindContent> normalizeContent(
            JSONArray contentArray, NormalizationContext normalizationContext) {
        if (contentArray == null || contentArray.isEmpty()) {
            return Collections.emptyList();
        }
        List<WindContent> content = new ArrayList<>(contentArray.size());
        for (int index = 0; index < contentArray.size(); index++) {
            JSONObject contentItem = contentArray.getJSONObject(index);
            if (contentItem == null) {
                continue;
            }
            String text = contentItem.getString("text");
            content.add(WindContent.builder()
                    .type(contentItem.getString("type"))
                    .text(normalizeText(text, normalizationContext))
                    .build());
        }
        return List.copyOf(content);
    }

    private String normalizeText(String text, NormalizationContext normalizationContext) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        try {
            Object parsed = JSON.parse(text);
            return JSON.toJSONString(normalizeValue(parsed, false, normalizationContext));
        } catch (JSONException ex) {
            return text;
        }
    }

    private JSONObject normalizeStructuredContent(
            JSONObject structuredContent, NormalizationContext normalizationContext) {
        if (structuredContent == null) {
            return null;
        }
        return (JSONObject) normalizeValue(structuredContent, false, normalizationContext);
    }

    private Object normalizeValue(
            Object value, boolean invalidScope, NormalizationContext normalizationContext) {
        if (value instanceof JSONObject objectValue) {
            JSONObject normalizedObject = new JSONObject(true);
            for (String key : objectValue.keySet()) {
                if ("excelTotalCount".equals(key)) {
                    normalizationContext.declaredCountSeen = true;
                }
                boolean childInvalidScope = invalidScope || "rows".equals(key) || "value".equals(key);
                normalizedObject.put(key, normalizeValue(
                        objectValue.get(key), childInvalidScope, normalizationContext));
            }
            return normalizedObject;
        }
        if (value instanceof JSONArray arrayValue) {
            JSONArray normalizedArray = new JSONArray(arrayValue.size());
            for (Object item : arrayValue) {
                normalizedArray.add(normalizeValue(item, invalidScope, normalizationContext));
            }
            return normalizedArray;
        }
        if (invalidScope && value instanceof String textValue
                && INVALID_VALUE.equalsIgnoreCase(textValue.trim())) {
            normalizationContext.invalidValueSeen = true;
            return null;
        }
        return value;
    }

    private List<JSONObject> createWarnings(NormalizationContext normalizationContext) {
        List<JSONObject> warnings = new ArrayList<>(2);
        if (normalizationContext.declaredCountSeen) {
            warnings.add(createWarning(
                    COUNT_WARNING_CODE,
                    "Wind 返回的声明总数可能与实际数据行数不一致，请以实际数据为准"));
        }
        if (normalizationContext.invalidValueSeen) {
            warnings.add(createWarning(
                    INVALID_WARNING_CODE,
                    "Wind 返回的结构化无效值已转换为 null"));
        }
        return List.copyOf(warnings);
    }

    private JSONObject createWarning(String code, String message) {
        JSONObject warning = new JSONObject(true);
        warning.put("code", code);
        warning.put("message", message);
        return warning;
    }

    private String extractContentText(JSONArray content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        JSONObject firstItem = content.getJSONObject(0);
        return firstItem == null ? "" : firstItem.getString("text");
    }

    private String requireCredential() {
        Optional<String> credential = credentialProvider.getApiKey();
        if (credential.isEmpty() || !StringUtils.hasText(credential.get())) {
            throw providerException(
                    FinancialProviderErrorCode.AUTHENTICATION_FAILED,
                    "Wind API Key 未配置", false);
        }
        return credential.get().trim();
    }

    private void validateServerType(WindServerType serverType) {
        if (serverType == null) {
            throw providerException(
                    FinancialProviderErrorCode.INVALID_REQUEST,
                    "Wind MCP 服务类型不能为空", false);
        }
    }

    private void validateCallRequest(WindToolCallRequest request) {
        if (request == null) {
            throw providerException(
                    FinancialProviderErrorCode.INVALID_REQUEST,
                    "Wind MCP 调用请求不能为空", false);
        }
        validateServerType(request.getServerType());
        if (!StringUtils.hasText(request.getToolName())) {
            throw providerException(
                    FinancialProviderErrorCode.INVALID_REQUEST,
                    "Wind MCP 工具名称不能为空", false);
        }
        if (!request.getServerType().isToolAllowed(request.getToolName())) {
            throw providerException(
                    FinancialProviderErrorCode.OUT_OF_SCOPE,
                    "Wind MCP 工具不在服务白名单内", false);
        }
    }

    private FinancialProviderException toHttpException(HttpStatusCode statusCode) {
        int status = statusCode.value();
        if (status == 401 || status == 403) {
            return providerException(
                    FinancialProviderErrorCode.AUTHENTICATION_FAILED,
                    "Wind MCP 身份认证失败", false);
        }
        if (status == 402) {
            return providerException(
                    FinancialProviderErrorCode.QUOTA_EXCEEDED,
                    "Wind MCP 调用额度不足", false);
        }
        if (status == 408 || status == 504) {
            return providerException(
                    FinancialProviderErrorCode.TIMEOUT,
                    "Wind MCP 请求超时", true);
        }
        if (status == 429) {
            return providerException(
                    FinancialProviderErrorCode.RATE_LIMITED,
                    "Wind MCP 请求频率受限", true);
        }
        if (statusCode.is4xxClientError()) {
            return providerException(
                    FinancialProviderErrorCode.INVALID_REQUEST,
                    "Wind MCP 拒绝了当前请求", false);
        }
        return providerException(
                FinancialProviderErrorCode.NETWORK_ERROR,
                "Wind MCP 服务暂不可用", true);
    }

    private FinancialProviderException providerException(FinancialProviderErrorCode errorCode) {
        return switch (errorCode) {
            case AUTHENTICATION_FAILED -> providerException(errorCode, "Wind MCP 身份认证失败", false);
            case RATE_LIMITED -> providerException(errorCode, "Wind MCP 请求频率受限", true);
            case QUOTA_EXCEEDED -> providerException(errorCode, "Wind MCP 调用额度不足", false);
            case INVALID_REQUEST -> providerException(errorCode, "Wind MCP 请求参数无效", false);
            case OUT_OF_SCOPE -> providerException(errorCode, "Wind MCP 请求超出能力范围", false);
            case NETWORK_ERROR -> providerException(errorCode, "Wind MCP 网络请求失败", true);
            case TIMEOUT -> providerException(errorCode, "Wind MCP 请求超时", true);
            case INVALID_RESPONSE -> providerException(errorCode, "Wind MCP 响应格式无效", false);
            default -> providerException(errorCode, "Wind MCP 服务返回业务错误", false);
        };
    }

    private FinancialProviderErrorCode classifyError(String message) {
        String normalizedMessage = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (normalizedMessage.contains("auth")
                || normalizedMessage.contains("api key")
                || normalizedMessage.contains("认证")
                || normalizedMessage.contains("鉴权")) {
            return FinancialProviderErrorCode.AUTHENTICATION_FAILED;
        }
        if (normalizedMessage.contains("quota")
                || normalizedMessage.contains("额度")
                || normalizedMessage.contains("积分")) {
            return FinancialProviderErrorCode.QUOTA_EXCEEDED;
        }
        if (normalizedMessage.contains("rate")
                || normalizedMessage.contains("频率")
                || normalizedMessage.contains("限流")) {
            return FinancialProviderErrorCode.RATE_LIMITED;
        }
        return FinancialProviderErrorCode.PROVIDER_ERROR;
    }

    private FinancialProviderException providerException(
            FinancialProviderErrorCode errorCode, String message, boolean retryable) {
        return new FinancialProviderException(FinancialProvider.WIND, errorCode, message, retryable);
    }

    /**
     * 单次响应规范化状态。
     */
    private static final class NormalizationContext {

        /** 是否发现声明总数字段。 */
        private boolean declaredCountSeen;
        /** 是否转换了无效值。 */
        private boolean invalidValueSeen;
    }
}
