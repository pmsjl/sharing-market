package com.pmsjl.job.cycle;

import com.pmsjl.model.entity.Commodity;
import com.pmsjl.service.CommodityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

import static com.pmsjl.constant.RedisConstants.CACHE_COMMODITY_KEY;
import static com.pmsjl.constant.RedisConstants.COMMODITY_VIEW_NUM_KEY;

@Slf4j
@Component
public class CommodityViewNumSyncJob {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CommodityService commodityService;

    /**
     * 每 5 分钟同步一次 Redis 中的商品浏览量增量到 MySQL
     */
    @Scheduled(cron = "0 */5 * * * ?")
    //通过这个注解实现定时任务，从右往左一次是任意周月日小时，每5分钟0秒运行一次
    public void syncViewNumToMysql() {
        String pattern = COMMODITY_VIEW_NUM_KEY + "*";

        Set<String> keys = stringRedisTemplate.keys(pattern);

        if (keys == null || keys.isEmpty()) {
            return;
        }

        for (String key : keys) {
            String value = null;

            try {
                // 1. 原子获取并删除 Redis 中的浏览量增量
                value = stringRedisTemplate.opsForValue().getAndDelete(key);


                if (value == null) {
                    continue;
                }
                //null说明没有访问没有创建redis的viewkey，所以不用更新

                long incrViewNum = Long.parseLong(value);

                if (incrViewNum <= 0) {
                    continue;
                }
                //小于等于0要么有问题，要么没有访问（其实这也应该是null），也不用管，只有大于0才有必要更新

                // 2. 从 key 中解析商品 id
                Long commodityId = Long.valueOf(key.replace(COMMODITY_VIEW_NUM_KEY, ""));

                // 3. MySQL 原子增加浏览量
                boolean updateResult = commodityService.lambdaUpdate()
                        .eq(Commodity::getId, commodityId)
                        .setSql("viewNum = IFNULL(viewNum, 0) + " + incrViewNum)
                        .update();

                if (updateResult) {
                    // 4. 同步成功后删除商品详情缓存
                    // 否则商品详情缓存里的 viewNum 还是旧的
                    stringRedisTemplate.delete(CACHE_COMMODITY_KEY + commodityId);

                    log.info("商品浏览量同步成功，commodityId={}, incrViewNum={}", commodityId, incrViewNum);
                } else {
                    // 5. MySQL 更新失败，把增量补回 Redis，避免浏览量丢失
                    stringRedisTemplate.opsForValue().increment(key, incrViewNum);

                    log.warn("商品浏览量同步失败，已回滚 Redis，commodityId={}, incrViewNum={}", commodityId, incrViewNum);
                }

            } catch (Exception e) {
                log.error("商品浏览量同步异常，key={}, value={}", key, value, e);

                // 如果已经 getAndDelete 取出了 value，但是后续异常了，要尽量补回 Redis
                if (value != null) {
                    try {
                        stringRedisTemplate.opsForValue().increment(key, Long.parseLong(value));
                    } catch (Exception ex) {
                        log.error("商品浏览量回滚 Redis 失败，key={}, value={}", key, value, ex);
                    }
                }
            }
        }
    }
}