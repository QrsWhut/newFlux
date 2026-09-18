package com.example.chat.dal.dao;

import com.example.chat.dal.model.AgentConversationDO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * Agent 会话 MyBatis Mapper。
 */
public interface AgentConversationMapper {

    /**
     * 按用户和会话标识查询会话。
     *
     * @param userId 用户标识
     * @param sessionId 会话标识
     * @return 会话记录，不存在时返回 null
     */
    AgentConversationDO selectByUserAndSession(
            @Param("userId") String userId,
            @Param("sessionId") String sessionId);

    /**
     * 按主键查询会话。
     *
     * @param id 会话主键
     * @return 会话记录，不存在时返回 null
     */
    AgentConversationDO selectById(@Param("id") Long id);

    /**
     * 插入会话。
     *
     * @param conversation 会话记录
     * @return 影响行数
     */
    int insert(AgentConversationDO conversation);

    /**
     * 使用乐观锁分配下一轮次号。
     *
     * @param id 会话主键
     * @param lockVersion 当前乐观锁版本
     * @return 影响行数
     */
    int incrementLatestTurnNo(
            @Param("id") Long id,
            @Param("lockVersion") Integer lockVersion);

    /**
     * 获取会话执行租约并推进隔离栅栏版本。
     *
     * @param id 会话主键
     * @param owner 租约持有者
     * @param currentTime 当前时间
     * @param expireTime 新租约过期时间
     * @return 影响行数
     */
    int acquireLease(
            @Param("id") Long id,
            @Param("owner") String owner,
            @Param("currentTime") LocalDateTime currentTime,
            @Param("expireTime") LocalDateTime expireTime);

    /**
     * 使用持有者和栅栏版本续租。
     *
     * @param id 会话主键
     * @param owner 租约持有者
     * @param executionEpoch 隔离栅栏版本
     * @param currentTime 当前时间
     * @param expireTime 新租约过期时间
     * @return 影响行数
     */
    int renewLease(
            @Param("id") Long id,
            @Param("owner") String owner,
            @Param("executionEpoch") Long executionEpoch,
            @Param("currentTime") LocalDateTime currentTime,
            @Param("expireTime") LocalDateTime expireTime);

    /**
     * 使用持有者和栅栏版本释放租约。
     *
     * @param id 会话主键
     * @param owner 租约持有者
     * @param executionEpoch 隔离栅栏版本
     * @return 影响行数
     */
    int releaseLease(
            @Param("id") Long id,
            @Param("owner") String owner,
            @Param("executionEpoch") Long executionEpoch);
}
