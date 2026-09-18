package com.example.chat.dal.dao;

import com.example.chat.dal.model.TurnToolCallDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 轮次工具调用 MyBatis Mapper。
 */
public interface TurnToolCallMapper {

    /**
     * 插入工具调用记录。
     *
     * @param toolCall 工具调用记录
     * @return 影响行数
     */
    int insert(TurnToolCallDO toolCall);

    /**
     * 查询多个轮次的工具调用。
     *
     * @param turnIds 轮次主键列表
     * @return 按轮次、步骤和调用顺序排列的工具调用
     */
    List<TurnToolCallDO> selectByTurnIds(@Param("turnIds") List<Long> turnIds);
}
