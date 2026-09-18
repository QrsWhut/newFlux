package com.example.chat.common.enums;

import java.util.Locale;
import java.util.Set;

/**
 * Wind MCP 服务类型及其固定安全边界。
 */
public enum WindServerType {

    /** 股票数据服务。 */
    STOCK_DATA(
            "https://mcp.wind.com.cn/vserver_stock_data/mcp/",
            Set.of(
                    "get_stock_price_indicators",
                    "get_risk_metrics",
                    "get_stock_events",
                    "get_stock_kline",
                    "get_stock_basicinfo",
                    "get_stock_equity_holders",
                    "get_stock_fundamentals",
                    "get_stock_quote",
                    "get_stock_technicals",
                    "search_stocks")),
    /** 基金数据服务。 */
    FUND_DATA(
            "https://mcp.wind.com.cn/vserver_fund_data/mcp/",
            Set.of(
                    "get_fund_price_indicators",
                    "get_fund_kline",
                    "get_fund_financials",
                    "get_fund_holdings",
                    "get_fund_company_info",
                    "get_fund_quote",
                    "get_fund_info",
                    "get_fund_holders",
                    "get_fund_performance",
                    "search_funds")),
    /** 指数和板块数据服务。 */
    INDEX_DATA(
            "https://mcp.wind.com.cn/vserver_index_data/mcp/",
            Set.of(
                    "get_index_technicals",
                    "get_index_quote",
                    "get_index_kline",
                    "get_index_fundamentals",
                    "get_index_price_indicators",
                    "get_index_basicinfo")),
    /** 债券数据服务。 */
    BOND_DATA(
            "https://mcp.wind.com.cn/vserver_bond_data/mcp/",
            Set.of(
                    "get_bond_basicinfo",
                    "get_bond_issuer_info",
                    "get_bond_market_data",
                    "get_bond_financial_data")),
    /** 金融公告和新闻检索服务。 */
    FINANCIAL_DOCS(
            "https://mcp.wind.com.cn/vserver_financial_docs/mcp/",
            Set.of("get_company_announcements", "get_financial_news")),
    /** 宏观和行业经济数据服务。 */
    ECONOMIC_DATA(
            "https://mcp.wind.com.cn/vserver_economic_data/mcp/",
            Set.of("search_economic_indicator", "query_economic_indicator_data")),
    /** 跨实体聚合分析数据服务。 */
    ANALYTICS_DATA(
            "https://mcp.wind.com.cn/vserver_analytics_data/mcp/",
            Set.of("get_financial_data"));

    /** 固定 MCP 服务地址。 */
    private final String endpoint;
    /** 允许调用的工具名集合。 */
    private final Set<String> allowedTools;

    WindServerType(String endpoint, Set<String> allowedTools) {
        this.endpoint = endpoint;
        this.allowedTools = allowedTools;
    }

    /**
     * 获取固定 MCP 服务地址。
     *
     * @return MCP 服务地址
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * 获取不可变工具白名单。
     *
     * @return 工具白名单
     */
    public Set<String> getAllowedTools() {
        return allowedTools;
    }

    /**
     * 判断工具是否属于当前服务。
     *
     * @param toolName 工具名称
     * @return 是否允许
     */
    public boolean isToolAllowed(String toolName) {
        return allowedTools.contains(toolName);
    }

    /**
     * 获取 Wind 协议使用的服务类型名称。
     *
     * @return 小写服务类型名称
     */
    public String getProtocolName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
