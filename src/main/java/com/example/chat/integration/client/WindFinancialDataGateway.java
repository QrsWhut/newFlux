package com.example.chat.integration.client;

import com.alibaba.fastjson.JSONObject;
import com.example.chat.common.dto.financial.FinancialCapabilityResult;
import com.example.chat.common.dto.financial.FinancialDataQuery;
import com.example.chat.common.dto.wind.WindToolCallRequest;
import com.example.chat.common.dto.wind.WindToolCallResult;
import com.example.chat.common.enums.FinancialAssetType;
import com.example.chat.common.enums.FinancialDataType;
import com.example.chat.common.enums.FinancialProvider;
import com.example.chat.common.enums.FinancialProviderErrorCode;
import com.example.chat.common.enums.WindServerType;
import com.example.chat.common.exception.FinancialProviderException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;

/**
 * 按资产领域和查询意图路由 Wind MCP 专项工具的金融数据网关。
 */
@Component("windFinancialDataGateway")
public class WindFinancialDataGateway implements FinancialDataGateway {

    /** 基金识别词。 */
    private static final List<String> FUND_KEYWORDS = List.of("基金", "ETF", "LOF");
    /** 指数识别词。 */
    private static final List<String> INDEX_KEYWORDS = List.of("指数", "板块");
    /** 债券识别词。 */
    private static final List<String> BOND_KEYWORDS = List.of("债券", "可转债", "信用债", "国债");
    /** 经济数据识别词。 */
    private static final List<String> ECONOMIC_KEYWORDS = List.of(
            "宏观", "行业", "汇率", "CPI", "PPI", "PMI", "利率", "产销量");
    /** Wind MCP 客户端。 */
    private final WindMcpClient windMcpClient;

    /**
     * 创建 Wind 金融数据网关。
     *
     * @param windMcpClient Wind MCP 客户端
     */
    public WindFinancialDataGateway(WindMcpClient windMcpClient) {
        this.windMcpClient = windMcpClient;
    }

    @Override
    public Mono<FinancialCapabilityResult> query(FinancialDataQuery query) {
        return Mono.defer(() -> {
            validateQuery(query);
            String question = buildQuestion(query);
            FinancialDataType dataType = resolveDataType(query, question);
            WindRoute route = dataType == FinancialDataType.AGGREGATION
                    ? createQuestionRoute(
                            WindServerType.ANALYTICS_DATA,
                            "get_financial_data",
                            question)
                    : routeByAsset(query, resolveAssetType(query, question), dataType, question);
            WindToolCallRequest request = WindToolCallRequest.builder()
                    .serverType(route.serverType())
                    .toolName(route.toolName())
                    .arguments(route.arguments())
                    .build();
            return windMcpClient.call(request).map(this::toCapabilityResult);
        });
    }

    private WindRoute routeByAsset(FinancialDataQuery query,
            FinancialAssetType assetType, FinancialDataType dataType, String question) {
        return switch (assetType) {
            case STOCK -> routeStock(query, dataType, question);
            case FUND -> routeFund(query, dataType, question);
            case INDEX -> routeIndex(query, dataType, question);
            case BOND -> routeBond(dataType, question);
            case ECONOMIC -> routeEconomic(query, dataType, question);
            case AUTO -> throw outOfScope("无法识别 Wind 查询的资产类型");
        };
    }

    private WindRoute routeStock(
            FinancialDataQuery query, FinancialDataType dataType, String question) {
        return switch (dataType) {
            case SNAPSHOT -> createSnapshotRoute(
                    query, WindServerType.STOCK_DATA, "get_stock_price_indicators");
            case KLINE -> createKlineRoute(query, WindServerType.STOCK_DATA, "get_stock_kline");
            case QUOTE -> createQuoteRoute(query, WindServerType.STOCK_DATA, "get_stock_quote", false);
            case BASIC_INFO -> createQuestionRoute(
                    WindServerType.STOCK_DATA, "get_stock_basicinfo", question);
            case FUNDAMENTALS -> createQuestionRoute(
                    WindServerType.STOCK_DATA, "get_stock_fundamentals", question);
            case HOLDINGS -> createQuestionRoute(
                    WindServerType.STOCK_DATA, "get_stock_equity_holders", question);
            case TECHNICAL -> createQuestionRoute(
                    WindServerType.STOCK_DATA, "get_stock_technicals", question);
            case RISK -> createQuestionRoute(
                    WindServerType.STOCK_DATA, "get_risk_metrics", question);
            case EVENTS -> createQuestionRoute(
                    WindServerType.STOCK_DATA, "get_stock_events", question);
            case SCREENING -> createQuestionRoute(
                    WindServerType.STOCK_DATA, "search_stocks", question);
            default -> throw outOfScope("Wind 股票服务不覆盖当前查询类型");
        };
    }

    private WindRoute routeFund(
            FinancialDataQuery query, FinancialDataType dataType, String question) {
        return switch (dataType) {
            case SNAPSHOT -> createSnapshotRoute(
                    query, WindServerType.FUND_DATA, "get_fund_price_indicators");
            case KLINE -> createKlineRoute(query, WindServerType.FUND_DATA, "get_fund_kline");
            case QUOTE -> createQuoteRoute(query, WindServerType.FUND_DATA, "get_fund_quote", false);
            case BASIC_INFO -> createQuestionRoute(
                    WindServerType.FUND_DATA, "get_fund_info", question);
            case FUNDAMENTALS -> createQuestionRoute(
                    WindServerType.FUND_DATA, "get_fund_financials", question);
            case HOLDINGS -> createQuestionRoute(
                    WindServerType.FUND_DATA, "get_fund_holdings", question);
            case PERFORMANCE -> createQuestionRoute(
                    WindServerType.FUND_DATA, "get_fund_performance", question);
            case SCREENING -> createQuestionRoute(
                    WindServerType.FUND_DATA, "search_funds", question);
            default -> throw outOfScope("Wind 基金服务不覆盖当前查询类型");
        };
    }

    private WindRoute routeIndex(
            FinancialDataQuery query, FinancialDataType dataType, String question) {
        return switch (dataType) {
            case SNAPSHOT -> createSnapshotRoute(
                    query, WindServerType.INDEX_DATA, "get_index_price_indicators");
            case KLINE -> createKlineRoute(query, WindServerType.INDEX_DATA, "get_index_kline");
            case QUOTE -> createQuoteRoute(query, WindServerType.INDEX_DATA, "get_index_quote", true);
            case BASIC_INFO -> createQuestionRoute(
                    WindServerType.INDEX_DATA, "get_index_basicinfo", question);
            case FUNDAMENTALS -> createQuestionRoute(
                    WindServerType.INDEX_DATA, "get_index_fundamentals", question);
            case TECHNICAL -> createQuestionRoute(
                    WindServerType.INDEX_DATA, "get_index_technicals", question);
            default -> throw outOfScope("Wind 指数服务不覆盖当前查询类型");
        };
    }

    private WindRoute routeBond(FinancialDataType dataType, String question) {
        return switch (dataType) {
            case BASIC_INFO -> createQuestionRoute(
                    WindServerType.BOND_DATA, "get_bond_basicinfo", question);
            case FUNDAMENTALS -> createQuestionRoute(
                    WindServerType.BOND_DATA, "get_bond_financial_data", question);
            case HOLDINGS -> createQuestionRoute(
                    WindServerType.BOND_DATA, "get_bond_issuer_info", question);
            case SNAPSHOT, KLINE, QUOTE -> createQuestionRoute(
                    WindServerType.BOND_DATA, "get_bond_market_data", question);
            default -> throw outOfScope("Wind 债券服务不覆盖当前查询类型");
        };
    }

    private WindRoute routeEconomic(
            FinancialDataQuery query,
            FinancialDataType dataType,
            String question) {
        if (dataType == FinancialDataType.BASIC_INFO || dataType == FinancialDataType.SCREENING) {
            return createQuestionRoute(
                    WindServerType.ECONOMIC_DATA, "search_economic_indicator", question);
        }
        boolean hasBeginDate = StringUtils.hasText(query.getBeginDate());
        boolean hasEndDate = StringUtils.hasText(query.getEndDate());
        boolean hasObservation = StringUtils.hasText(query.getObservation());
        if (hasBeginDate != hasEndDate) {
            throw invalidRequest("Wind 经济数据的开始和结束日期必须成对提供");
        }
        if (hasObservation && hasBeginDate) {
            throw invalidRequest("Wind 经济数据的日期范围与观测期数互斥");
        }
        if (!hasObservation && !hasBeginDate) {
            throw outOfScope("Wind 经济数据查询需要日期范围或观测期数");
        }
        JSONObject arguments = new JSONObject(true);
        arguments.put("question", question);
        if (hasObservation) {
            String observation = query.getObservation().trim();
            if (observation.isEmpty()
                    || !observation.chars().allMatch(Character::isDigit)) {
                throw invalidRequest("Wind 经济数据观测期数必须为数字字符串");
            }
            arguments.put("observation", observation);
        } else {
            arguments.put("beginDate", query.getBeginDate().trim());
            arguments.put("endDate", query.getEndDate().trim());
        }
        return new WindRoute(
                WindServerType.ECONOMIC_DATA,
                "query_economic_indicator_data",
                arguments);
    }

    private WindRoute createSnapshotRoute(
            FinancialDataQuery query, WindServerType serverType, String toolName) {
        String entity = requireEntity(query);
        JSONObject arguments = new JSONObject(true);
        arguments.put("windcode", entity);
        if (query.getMetricNames() != null && !query.getMetricNames().isEmpty()) {
            arguments.put("indexes", String.join(",", query.getMetricNames()));
        }
        return new WindRoute(serverType, toolName, arguments);
    }

    private WindRoute createKlineRoute(
            FinancialDataQuery query, WindServerType serverType, String toolName) {
        String entity = requireEntity(query);
        if (!StringUtils.hasText(query.getBeginDate()) || !StringUtils.hasText(query.getEndDate())) {
            throw outOfScope("Wind K 线查询需要明确的开始和结束日期");
        }
        JSONObject arguments = new JSONObject(true);
        arguments.put("windcode", entity);
        arguments.put("begin_date", query.getBeginDate().trim());
        arguments.put("end_date", query.getEndDate().trim());
        return new WindRoute(serverType, toolName, arguments);
    }

    private WindRoute createQuoteRoute(FinancialDataQuery query,
            WindServerType serverType, String toolName, boolean datesRequired) {
        String entity = requireEntity(query);
        if (datesRequired
                && (!StringUtils.hasText(query.getBeginDate()) || !StringUtils.hasText(query.getEndDate()))) {
            throw outOfScope("Wind 指数分钟行情查询需要明确的开始和结束日期");
        }
        JSONObject arguments = new JSONObject(true);
        arguments.put("windcode", entity);
        if (StringUtils.hasText(query.getBeginDate())) {
            arguments.put("begin", query.getBeginDate().trim());
        }
        if (StringUtils.hasText(query.getEndDate())) {
            arguments.put("end", query.getEndDate().trim());
        }
        return new WindRoute(serverType, toolName, arguments);
    }

    private WindRoute createQuestionRoute(
            WindServerType serverType, String toolName, String question) {
        JSONObject arguments = new JSONObject(true);
        arguments.put("question", question);
        return new WindRoute(serverType, toolName, arguments);
    }

    private FinancialAssetType resolveAssetType(FinancialDataQuery query, String question) {
        if (query.resolveAssetType() != FinancialAssetType.AUTO) {
            return query.resolveAssetType();
        }
        String entity = query.getEntity();
        String normalizedEntity = entity == null ? "" : entity.toUpperCase(Locale.ROOT);
        if (normalizedEntity.endsWith(".OF") || containsAny(question, FUND_KEYWORDS)) {
            return FinancialAssetType.FUND;
        }
        if (containsAny(question, INDEX_KEYWORDS)) {
            return FinancialAssetType.INDEX;
        }
        if (containsAny(question, BOND_KEYWORDS)) {
            return FinancialAssetType.BOND;
        }
        if (containsAny(question, ECONOMIC_KEYWORDS)) {
            return FinancialAssetType.ECONOMIC;
        }
        if (StringUtils.hasText(entity) || question.contains("股票") || question.contains("股价")) {
            return FinancialAssetType.STOCK;
        }
        return FinancialAssetType.AUTO;
    }

    private FinancialDataType resolveDataType(FinancialDataQuery query, String question) {
        if (query.resolveDataType() != FinancialDataType.AUTO) {
            return query.resolveDataType();
        }
        if (containsAny(question, List.of("聚合", "加权平均", "排名", "复合指标"))) {
            return FinancialDataType.AGGREGATION;
        }
        if (containsAny(question, List.of("筛选", "选股", "选基金"))) {
            return FinancialDataType.SCREENING;
        }
        if (containsAny(question, List.of("K线", "K 线", "历史行情", "区间走势"))) {
            return FinancialDataType.KLINE;
        }
        if (containsAny(question, List.of("分钟", "分时"))) {
            return FinancialDataType.QUOTE;
        }
        if (containsAny(question, List.of("档案", "基本资料", "公司信息"))) {
            return FinancialDataType.BASIC_INFO;
        }
        if (containsAny(question, List.of("财务", "估值", "PE", "PB", "ROE", "营收", "净利润"))) {
            return FinancialDataType.FUNDAMENTALS;
        }
        if (containsAny(question, List.of("持仓", "股东", "股本"))) {
            return FinancialDataType.HOLDINGS;
        }
        if (containsAny(question, List.of("业绩", "收益率"))) {
            return FinancialDataType.PERFORMANCE;
        }
        if (containsAny(question, List.of("技术指标", "MACD", "KDJ", "RSI"))) {
            return FinancialDataType.TECHNICAL;
        }
        if (containsAny(question, List.of("风险", "Beta", "VaR", "回撤"))) {
            return FinancialDataType.RISK;
        }
        if (containsAny(question, List.of("事件", "分红", "解禁", "增减持"))) {
            return FinancialDataType.EVENTS;
        }
        if (containsAny(question, List.of("最新", "当前", "行情", "价格", "涨跌幅"))) {
            return FinancialDataType.SNAPSHOT;
        }
        return FinancialDataType.AUTO;
    }

    private String buildQuestion(FinancialDataQuery query) {
        StringBuilder questionBuilder = new StringBuilder(query.getQuestion().trim());
        if (query.getMetricNames() != null && !query.getMetricNames().isEmpty()) {
            questionBuilder.append("，指标：").append(String.join("、", query.getMetricNames()));
        }
        if (StringUtils.hasText(query.getTimeRange())) {
            questionBuilder.append("，时间范围：").append(query.getTimeRange().trim());
        }
        return questionBuilder.toString();
    }

    private String requireEntity(FinancialDataQuery query) {
        if (!StringUtils.hasText(query.getEntity())) {
            throw outOfScope("Wind 专项行情查询需要明确的标的名称或 Wind 代码");
        }
        return query.getEntity().trim();
    }

    private boolean containsAny(String text, List<String> keywords) {
        String normalizedText = text.toUpperCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (normalizedText.contains(keyword.toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private FinancialCapabilityResult toCapabilityResult(WindToolCallResult result) {
        return FinancialCapabilityResult.builder()
                .provider(FinancialProvider.WIND)
                .data(result.toDataText())
                .source(result.getSource())
                .warnings(result.getWarnings())
                .serverType(result.getServerType().getProtocolName())
                .toolName(result.getToolName())
                .build();
    }

    private void validateQuery(FinancialDataQuery query) {
        if (query == null || query.getQuestion() == null || query.getQuestion().isBlank()) {
            throw new FinancialProviderException(
                    FinancialProvider.WIND,
                    FinancialProviderErrorCode.INVALID_REQUEST,
                    "Wind 金融数据查询问题不能为空",
                    false);
        }
    }

    private FinancialProviderException outOfScope(String message) {
        return new FinancialProviderException(
                FinancialProvider.WIND,
                FinancialProviderErrorCode.OUT_OF_SCOPE,
                message,
                false);
    }

    private FinancialProviderException invalidRequest(String message) {
        return new FinancialProviderException(
                FinancialProvider.WIND,
                FinancialProviderErrorCode.INVALID_REQUEST,
                message,
                false);
    }

    /**
     * Wind MCP 调用路由。
     *
     * @param serverType MCP 服务类型
     * @param toolName MCP 工具名称
     * @param arguments MCP 工具参数
     */
    private record WindRoute(
            WindServerType serverType,
            String toolName,
            JSONObject arguments) {
    }
}
