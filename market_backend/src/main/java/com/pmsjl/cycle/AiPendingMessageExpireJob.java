package com.pmsjl.cycle;

import com.pmsjl.config.AiAgentProperties;
import com.pmsjl.service.AiMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 修复因进程中断或最终回写失败而遗留的超时 PENDING 助手消息。
 * 所以现在pending有这几种情况：
 * 1.正常处理正常变为success，没啥问题。
 * 2.出现异常捕捉后，变成failed。
 * 3.出现无法被捕捉的异常导致程序中断或没有出现异常，但是超时，此时出现下一条消息检测出超时更新为failed。
 * 4.出现3的异常但是没有下一条消息检测。
 * 我们定时清除的就是第4种
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiPendingMessageExpireJob {

    private static final int EXPIRE_BATCH_SIZE = 100;

    private final AiMessageService aiMessageService;
    private final AiAgentProperties aiAgentProperties;

    /**
     * 每分钟第 30 秒扫描一次，与整分钟运行的订单任务错峰。
     */
    @Scheduled(cron = "30 * * * * ?")
    public void expireStalePendingMessages() {
        Date expireBefore = new Date(
                System.currentTimeMillis() - aiAgentProperties.getPendingTimeoutMs());
        try {
            int expiredCount = aiMessageService.expireStalePendingMessages(
                    expireBefore, EXPIRE_BATCH_SIZE);
            if (expiredCount > 0) {
                log.info("超时 AI 助手消息清理完成，expiredCount={}", expiredCount);
            }
        } catch (Exception exception) {
            log.error("扫描超时 AI 助手消息失败", exception);
        }
    }
}
