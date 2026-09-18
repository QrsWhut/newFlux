package com.example.chat.common.dto.financial;

import com.example.chat.common.enums.FinancialDocumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 金融文档检索请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialDocumentQuery {

    /** 检索问题。 */
    private String query;
    /** 文档类型。 */
    private FinancialDocumentType documentType;
    /** 最大返回条数。 */
    private Integer topK;
    /** 当前会话标识。 */
    private String sessionId;

    /**
     * 获取有效文档类型。
     *
     * @return 文档类型
     */
    public FinancialDocumentType resolveDocumentType() {
        return documentType == null ? FinancialDocumentType.AUTO : documentType;
    }

    /**
     * 获取有效最大返回条数。
     *
     * @return 最大返回条数
     */
    public int resolveTopK() {
        return topK == null ? 5 : topK;
    }
}
