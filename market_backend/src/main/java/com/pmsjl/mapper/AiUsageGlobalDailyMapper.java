package com.pmsjl.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pmsjl.model.entity.AiUsageGlobalDaily;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.Date;

@Mapper
public interface AiUsageGlobalDailyMapper extends BaseMapper<AiUsageGlobalDaily> {
    @Insert("""
            INSERT IGNORE INTO ai_usage_global_daily
                (usageDate, requestCount, successCount, failedCount,
                 inputTokens, outputTokens, createTime, updateTime)
            VALUES
                (#{usageDate}, 0, 0, 0, 0, 0, NOW(), NOW())
            """)
    int insertUsageGlobalDaily(@Param("usageDate") LocalDate usageDate);

    @Update("""
            UPDATE ai_usage_global_daily
            SET requestCount = requestCount + 1,
                lastRequestTime = #{requestTime},
                updateTime = #{requestTime}
            WHERE usageDate = #{usageDate}
              AND requestCount < #{limit}
            """)
    int updateRequestCount(@Param("usageDate") LocalDate usageDate,
                           @Param("limit") int limit,
                           @Param("requestTime") Date requestTime);

    @Update("""
            UPDATE ai_usage_global_daily
            SET successCount = successCount + 1,
                inputTokens = inputTokens + #{inputTokens},
                outputTokens = outputTokens + #{outputTokens},
                updateTime = NOW()
            WHERE usageDate = #{usageDate}
            """)
    int recordSuccess(@Param("usageDate") LocalDate usageDate,
                      @Param("inputTokens") long inputTokens,
                      @Param("outputTokens") long outputTokens);

    @Update("""
            UPDATE ai_usage_global_daily
            SET failedCount = failedCount + 1, updateTime = NOW()
            WHERE usageDate = #{usageDate}
            """)
    int recordFailure(@Param("usageDate") LocalDate usageDate);
}
