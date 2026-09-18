package com.example.chat.common.dto.financial;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.example.chat.common.enums.FinancialProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 金融能力统一结果。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FinancialCapabilityResult {

    /** 实际提供数据的能力方。 */
    private FinancialProvider provider;
    /** 业务数据文本。 */
    private String data;
    /** 数据来源说明。 */
    private String source;
    /** 安全、可展示的机器警告。 */
    private List<JSONObject> warnings;
    /** 实际 Wind 服务类型。 */
    private String serverType;
    /** 实际 Wind 工具名称。 */
    private String toolName;

    /**
     * 生成提供给模型的稳定观察结果。
     *
     * @return JSON 观察文本
     */
    public String toObservation() {
        JSONObject payload = new JSONObject(true);
        payload.put("provider", provider == null ? "UNKNOWN" : provider.name());
        payload.put("source", source == null ? "" : source);
        payload.put("warnings", warnings == null ? Collections.emptyList() : warnings);
        if (serverType != null) {
            payload.put("serverType", serverType);
        }
        if (toolName != null) {
            payload.put("toolName", toolName);
        }
        payload.put("data", parseData());
        return payload.toJSONString();
    }

    /**
     * 追加一条提供方回退警告。
     *
     * @param warning 安全警告
     * @return 新结果对象
     */
    public FinancialCapabilityResult withWarning(JSONObject warning) {
        List<JSONObject> updatedWarnings = new ArrayList<>();
        if (warnings != null) {
            updatedWarnings.addAll(warnings);
        }
        updatedWarnings.add(warning);
        return toBuilder().warnings(List.copyOf(updatedWarnings)).build();
    }

    private Object parseData() {
        if (data == null) {
            return "";
        }
        try {
            return JSON.parse(data);
        } catch (JSONException ex) {
            return data;
        }
    }
}
