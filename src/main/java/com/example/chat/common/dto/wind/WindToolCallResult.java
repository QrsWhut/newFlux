package com.example.chat.common.dto.wind;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.example.chat.common.enums.WindServerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Wind MCP 工具调用结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WindToolCallResult {

    /** Wind MCP 服务类型。 */
    private WindServerType serverType;
    /** 实际调用的工具名称。 */
    private String toolName;
    /** MCP 内容块。 */
    private List<WindContent> content;
    /** MCP 结构化内容。 */
    private JSONObject structuredContent;
    /** 结果规范化警告。 */
    private List<JSONObject> warnings;
    /** 数据来源。 */
    private String source;

    /**
     * 将 MCP 业务内容转换为稳定 JSON 文本。
     *
     * @return 业务数据 JSON 文本
     */
    public String toDataText() {
        if (structuredContent != null && !structuredContent.isEmpty()) {
            return structuredContent.toJSONString();
        }
        JSONArray contentArray = new JSONArray();
        if (content != null) {
            for (WindContent contentItem : content) {
                contentArray.add(toContentValue(contentItem));
            }
        }
        if (contentArray.size() == 1) {
            return JSON.toJSONString(contentArray.get(0));
        }
        return contentArray.toJSONString();
    }

    private Object toContentValue(WindContent contentItem) {
        if (contentItem == null) {
            return null;
        }
        String text = contentItem.getText();
        if (text == null) {
            return "";
        }
        try {
            return JSON.parse(text);
        } catch (JSONException ex) {
            return text;
        }
    }
}
