package com.example.chat.dal.dao;

import com.example.chat.dal.model.ConversationSummaryDO;
import org.apache.ibatis.annotations.Param;

/**
 * 会话摘要 MyBatis Mapper。
 */
public interface ConversationSummaryMapper {

    /**
     * 按主键查询摘要。
     *
     * @param id 摘要主键
     * @return 摘要记录，不存在时返回 null
     */
    ConversationSummaryDO selectById(@Param("id") Long id);

    /**
     * 插入摘要记录。
     *
     * @param summary 摘要记录
     * @return 影响行数
     */
    int insert(ConversationSummaryDO summary);
}
