package com.example.chat.agent.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.example.chat.agent.model.AgentToolDefinition;
import com.example.chat.common.dto.agent.tool.QueryFinancialDataInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工具 Schema 自动生成测试。
 */
public class AgentToolSchemaGeneratorTest {

    @Test
    public void testGenerateSchemaFromStrongTypeAndValidationAnnotations() {
        AgentToolDefinition definition = new AgentToolSchemaGenerator()
                .createDefinition(DpuAgentTool.class, QueryFinancialDataInput.class);
        JSONObject schema = definition.getFunction().getParameters();
        JSONObject properties = schema.getJSONObject("properties");

        assertEquals("queryFinancialData", definition.getFunction().getName());
        assertEquals("查询股票、基金、指数的行情、财务指标与量化计算结果。",
                definition.getFunction().getDescription());
        assertTrue(definition.getFunction().getStrict());
        assertEquals("object", schema.getString("type"));
        assertFalse(schema.getBooleanValue("additionalProperties"));
        assertTrue(schema.getJSONArray("required").contains("query"));
        assertTrue(schema.getJSONArray("required").contains("metricNames"));
        assertTrue(schema.getJSONArray("required").contains("timeRange"));
        assertEquals("string",
                properties.getJSONObject("query").getString("type"));
        assertEquals(500,
                properties.getJSONObject("query").getIntValue("maxLength"));
        assertEquals("用于查询行情或数据计算的标的名和问题",
                properties.getJSONObject("query").getString("description"));
        JSONArray metricTypes = properties.getJSONObject("metricNames").getJSONArray("type");
        assertTrue(metricTypes.contains("array"));
        assertTrue(metricTypes.contains("null"));
        assertEquals(100, properties.getJSONObject("metricNames")
                .getJSONObject("items").getIntValue("maxLength"));
    }
}
