package com.pmsjl.job.cycle;

import com.pmsjl.common.ErrorCode;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.model.entity.Commodity;
import com.pmsjl.model.entity.CommodityOrder;
import com.pmsjl.service.CommodityOrderService;
import com.pmsjl.service.CommodityService;
import com.pmsjl.utils.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.List;

import static com.pmsjl.constant.RedisConstants.CACHE_COMMODITY_KEY;

@Slf4j
@Component
/***
 * 这里的订单超时释放库存对应的是buy，
 * 源码是只有前端有所谓的倒计时
 * 但实际上后端没有做任何处理，这显然是不合理的
 * 所以我们设置了无论是否支付都扣减库存
 * 然后后端启动定时任务释放库存，设置过期状态
 */
public class CommodityOrderExpireReleaseJob {

    private static final int PAY_STATUS_UNPAID = 0;
    private static final int PAY_STATUS_EXPIRED = 2;
    private static final int ORDER_EXPIRE_MINUTES = 15;
    private static final int EXPIRE_BATCH_SIZE = 100;

    @Autowired
    private CommodityOrderService commodityOrderService;

    @Autowired
    private CommodityService commodityService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    /**
     * 每分钟释放一次超时未支付订单预占的库存。
     */
    @Scheduled(cron = "0 * * * * ?")
    public void releaseExpiredUnpaidOrders() {
        Date expireBefore = new Date(System.currentTimeMillis() - ORDER_EXPIRE_MINUTES * 60_000L);
        //15分钟对应15个60000ms
        List<CommodityOrder> expiredOrders = commodityOrderService.lambdaQuery()
                .eq(CommodityOrder::getPayStatus, PAY_STATUS_UNPAID)
                .eq(CommodityOrder::getIsDelete,0)
                .lt(CommodityOrder::getCreateTime, expireBefore)
                .last("LIMIT " + EXPIRE_BATCH_SIZE)
                .list();
        //获取超时待释放的订单，一次处理 EXPIRE_BATCH_SIZE个订单以减少性能消耗
        //同时这里我添加联合索引提高搜索效率

        if (expiredOrders == null || expiredOrders.isEmpty()) {
            return;
        }

        for (CommodityOrder order : expiredOrders) {
            try {
                Boolean released = transactionTemplate.execute(status -> releaseOneOrder(order));
                //注意这里的transcationTemplate类似于我们之前的@Transactional，他的作用就是手动的事务管理
                //在status->{}这里的任务中如果抛出异常则会自动回滚
                //这里
                if (Boolean.TRUE.equals(released)) {
                    log.info("超时订单库存释放成功，orderId={}, commodityId={}, buyNumber={}",
                            order.getId(), order.getCommodityId(), order.getBuyNumber());
                }
            } catch (Exception e) {
                log.error("超时订单库存释放失败，orderId={}, commodityId={}",
                        order.getId(), order.getCommodityId(), e);
            }
        }
    }

    private Boolean releaseOneOrder(CommodityOrder order) {
        Integer buyNumber = order.getBuyNumber();
        ThrowUtils.throwIf(buyNumber == null || buyNumber <= 0,
                ErrorCode.OPERATION_ERROR, "过期订单购买数量异常");

        boolean orderExpired = commodityOrderService.lambdaUpdate()
                .set(CommodityOrder::getPayStatus, PAY_STATUS_EXPIRED)
                .eq(CommodityOrder::getId, order.getId())
                .eq(CommodityOrder::getPayStatus, PAY_STATUS_UNPAID)
                .eq(CommodityOrder::getIsDelete, 0)
                .update();
        if (!orderExpired) {
            return false;
        }
        //将订单状态调整为过期，前端也会随之更改
        //我们这里的返回false不算是异常情况，他这里相当于是多线程更新，
        // 由于mysql的行锁阻塞导致其他线程更新重复所以失败，
        // 不是异常情况

        boolean inventoryReleased = commodityService.lambdaUpdate()
                .setSql("commodityInventory = IFNULL(commodityInventory, 0) + " + buyNumber)
                .eq(Commodity::getId, order.getCommodityId())
                .eq(Commodity::getIsDelete, 0)
                .update();
        ThrowUtils.throwIf(!inventoryReleased, ErrorCode.OPERATION_ERROR, "商品库存释放失败");

        try {
            stringRedisTemplate.delete(CACHE_COMMODITY_KEY + order.getCommodityId());
        } catch (Exception e) {
            log.warn("超时订单库存已释放，但商品缓存删除失败，commodityId={}", order.getCommodityId(), e);
        }

        return true;
    }
}
