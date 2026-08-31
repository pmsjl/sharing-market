package com.pmsjl.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

@Data
@TableName("ai_usage_daily")
public class AiUsageDaily implements Serializable {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private LocalDate usageDate;
    private Integer requestCount;
    private Integer successCount;
    private Integer failedCount;
    private Long inputTokens;
    private Long outputTokens;
    private Date lastRequestTime;
    private Date createTime;
    private Date updateTime;

    private static final long serialVersionUID = 1L;
}
