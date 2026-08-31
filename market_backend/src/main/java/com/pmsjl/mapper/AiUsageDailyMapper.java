package com.pmsjl.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pmsjl.model.entity.AiUsageDaily;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.Date;

@Mapper
public interface AiUsageDailyMapper extends BaseMapper<AiUsageDaily> {
    @Insert("""
            INSERT INTO ai_usage_daily
                (id,userId, usageDate, requestCount, successCount, failedCount,
                 inputTokens, outputTokens, createTime, updateTime)
            VALUES
                (#{id},#{userId}, #{usageDate}, 0, 0, 0, 0, 0, NOW(), NOW())
            ON DUPLICATE KEY UPDATE id=id
            """)
    int insertUsageUserDaily(@Param("id") Long id, @Param("userId") Long userId,
                             @Param("usageDate") LocalDate usageDate);

    @Update("""
            UPDATE ai_usage_daily
            SET requestCount = requestCount + 1,
                lastRequestTime = #{requestTime},
                updateTime = #{requestTime}
            WHERE userId = #{userId}
              AND usageDate = #{usageDate}
              AND requestCount < #{limit}
            """)
    int updateRequestCount(@Param("userId") Long userId,
                           @Param("usageDate") LocalDate usageDate,
                           @Param("limit") int limit,
                           @Param("requestTime") Date requestTime);

    @Update("""
            UPDATE ai_usage_daily
            SET successCount = successCount + 1,
                inputTokens = inputTokens + #{inputTokens},
                outputTokens = outputTokens + #{outputTokens},
                updateTime = NOW()
            WHERE userId = #{userId} AND usageDate = #{usageDate}
            """)
    int recordSuccess(@Param("userId") Long userId,
                      @Param("usageDate") LocalDate usageDate,
                      @Param("inputTokens") long inputTokens,
                      @Param("outputTokens") long outputTokens);

    @Update("""
            UPDATE ai_usage_daily
            SET failedCount = failedCount + 1, updateTime = NOW()
            WHERE userId = #{userId} AND usageDate = #{usageDate}
            """)
    int recordFailure(@Param("userId") Long userId,
                      @Param("usageDate") LocalDate usageDate);
}
