package com.example.chat.dal.dao;

import com.example.chat.dal.model.ModelCallRecordDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 模型调用记录 MyBatis Mapper。
 */
public interface ModelCallRecordMapper {

    /**
     * 插入模型调用记录。
     *
     * @param modelCall 模型调用记录
     * @return 影响行数
     */
    int insert(ModelCallRecordDO modelCall);

    /**
     * 按主键查询模型调用记录。
     *
     * @param id 模型调用主键
     * @return 模型调用记录，不存在时返回 null
     */
    /**
     * 按 Turn 查询模型调用事实。
     *
     * @param turnId Turn 主键
     * @return 按步骤和尝试序号排列的模型调用
     */
    List<ModelCallRecordDO> selectByTurnId(@Param("turnId") Long turnId);
    ModelCallRecordDO selectById(@Param("id") Long id);
}
