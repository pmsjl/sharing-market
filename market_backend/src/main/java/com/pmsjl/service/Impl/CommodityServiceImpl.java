package com.pmsjl.service.Impl;

import static com.pmsjl.constant.RedisConstants.*;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.model.dto.commodity.BuyCommodityRequest;
import com.pmsjl.model.dto.commodity.CommodityQueryRequest;
import com.pmsjl.model.dto.commodityOrder.PayCommodityOrderRequest;
import com.pmsjl.model.entity.Commodity;
import com.pmsjl.mapper.CommodityMapper;
import com.pmsjl.model.entity.CommodityOrder;
import com.pmsjl.model.entity.CommodityType;
import com.pmsjl.model.entity.User;
import com.pmsjl.model.vo.CommodityVO;
import com.pmsjl.service.CommodityOrderService;
import com.pmsjl.service.CommodityService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pmsjl.service.CommodityTypeService;
import com.pmsjl.service.UserService;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author pmsjl
 * @since 2026-04-07
 */
@Service
public class CommodityServiceImpl extends ServiceImpl<CommodityMapper, Commodity> implements CommodityService {
    private static final long ORDER_PAY_EXPIRE_MILLIS = TimeUnit.MINUTES.toMillis(15);
    private static final Set<String> ALLOWED_COMMODITY_SORT_FIELDS = Set.of(
            "id", "commodityName", "commodityTypeId", "adminId", "isListed",
            "commodityInventory", "price", "viewNum", "favourNum", "createTime", "updateTime"
    );

    @Autowired
    private UserService userService;
    @Autowired
    private CommodityOrderService commodityOrderService;
    @Autowired
    private CommodityTypeService commodityTypeService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    ObjectMapper objectMapper;


    //TODO:这里原本接口只有管理员可以调用，前端就是个半成品，
    // 实际上应该时普通和管理员全都能调用，但是普通用户有些值无法设置（已通过前端实现）
    // 因此下方做了null判断手动赋值，进行兜底

    @Override
    public Long addCommodity(Commodity commodity, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long id = loginUser.getId();
        commodity.setAdminId(id);
        if (commodity.getIsListed() == null) {
            commodity.setIsListed(0);
        }

        if (commodity.getViewNum() == null) {
            commodity.setViewNum(0);
        }

        if (commodity.getFavourNum() == null) {
            commodity.setFavourNum(0);
        }
        commodity.setCreateTime(DateTime.now());
        commodity.setUpdateTime(DateTime.now());
        validCommodity(commodity);
        boolean result = this.save(commodity);
        ThrowUtils.throwIf(result == false, ErrorCode.OPERATION_ERROR);
        Long commodityId = commodity.getId();
        return commodityId;

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> buyCommodity(BuyCommodityRequest buyCommodityRequest, HttpServletRequest request) {
        Long commodityId = buyCommodityRequest.getCommodityId();
        Integer buyNumber = buyCommodityRequest.getBuyNumber();
        ThrowUtils.throwIf(commodityId == null || commodityId <= 0, ErrorCode.PARAMS_ERROR, "商品id非法");
        ThrowUtils.throwIf(buyNumber == null || buyNumber <= 0, ErrorCode.PARAMS_ERROR, "购买数量非法");

        User loginUser = userService.getLoginUser(request);
        User user = userService.getById(loginUser.getId());
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        Commodity commodity = getById(commodityId);
        ThrowUtils.throwIf(commodity == null, ErrorCode.NOT_FOUND_ERROR, "商品不存在");
        ThrowUtils.throwIf(!Objects.equals(commodity.getIsListed(), 1), ErrorCode.OPERATION_ERROR, "商品未上架");
        ThrowUtils.throwIf(commodity.getPrice() == null || commodity.getPrice().compareTo(BigDecimal.ZERO) <= 0,
                ErrorCode.OPERATION_ERROR, "商品价格异常");

        BigDecimal totalAmount = commodity.getPrice().multiply(BigDecimal.valueOf(buyNumber));
        BigDecimal balance = user.getBalance() == null ? BigDecimal.ZERO : user.getBalance();
        boolean balanceEnough = balance.compareTo(totalAmount) >= 0;

        boolean inventoryReserved = lambdaUpdate()
                .setSql("commodityInventory = commodityInventory - " + buyNumber)
                .eq(Commodity::getId, commodityId)
                .eq(Commodity::getIsListed, 1)
                .eq(Commodity::getIsDelete, 0)
                .ge(Commodity::getCommodityInventory, buyNumber)
                .update();
        //注意这里采取了不管余额如何变化都扣除库存的操作，之后会补上，超时未支付重新释放库存的操作
        ThrowUtils.throwIf(!inventoryReserved, ErrorCode.OPERATION_ERROR, "库存不足或商品状态已变化");

        CommodityOrder order = new CommodityOrder();
        order.setUserId(user.getId());
        order.setCommodityId(commodityId);
        order.setBuyNumber(buyNumber);
        order.setPaymentAmount(totalAmount);
        order.setRemark(buyCommodityRequest.getRemark());
        order.setPayStatus(balanceEnough ? 1 : 0);
        boolean orderSaved = commodityOrderService.save(order);
        ThrowUtils.throwIf(!orderSaved, ErrorCode.OPERATION_ERROR, "订单创建失败");

        if (balanceEnough) {
            boolean balanceDeducted = userService.lambdaUpdate()
                    .setSql("balance = balance - " + totalAmount.toPlainString())
                    .eq(User::getId, user.getId())
                    .eq(User::getIsDelete, 0)
                    .ge(User::getBalance, totalAmount)
                    .update();
            ThrowUtils.throwIf(!balanceDeducted, ErrorCode.OPERATION_ERROR, "余额扣减失败");
        }
        //这里对余额进行扣除，为什么需要再次判断，因为多线程会导致可能上面的余额是够的，然后paystatus为已支付
        //但是到这里余额又不够了，所以要判断，扣除失败就全部回滚

        stringRedisTemplate.delete(CACHE_COMMODITY_KEY + commodityId);
        //库存已经变化，所以删除redis缓存

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", order.getId());
        result.put("payStatus", order.getPayStatus());
        result.put("needPay", !balanceEnough);
        //这里乍一看paystatus和needpay作用是一样的
        //但是如果我后续继续添加其他的paystatus状态，（释放库存需要有过期状态之类的）
        //那两者就不等价了
        //前端读取needPay弹出是否支付成功的信息
        //然后再利用paystatus选择订单展示的情况（有无倒计时）
        //这里的我们在原有基础上添加了paystatus=2的过期状态
        //因此需要定时任务去扫描是否有过期订单
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean payCommodity(PayCommodityOrderRequest payRequest, HttpServletRequest request) {
        Long orderId = payRequest.getCommodityOrderId();
        ThrowUtils.throwIf(orderId == null || orderId <= 0, ErrorCode.PARAMS_ERROR, "订单id非法");

        User loginUser = userService.getLoginUser(request);

        CommodityOrder order = commodityOrderService.getByIdWithLock(orderId);
        //设置锁一般来说要么根据写锁判断多线程问题，要么正常读加乐观锁进行判断，要么全局悲观锁
        //这里采取的是在普通的查询语句基础上加上了 FOR UPDATE他的作用不是说是什么更新
        //而是设置写锁的标志，相当于设置悲观锁，在整个方法事务提交之前始终都有写锁！！！

        ThrowUtils.throwIf(order == null, ErrorCode.NOT_FOUND_ERROR, "订单不存在");

        if (!order.getUserId().equals(loginUser.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无法操作他人订单");
        }

        if (!Objects.equals(order.getPayStatus(), 0)) {
            String message = Objects.equals(order.getPayStatus(), 1)
                    ? "订单已支付，无需重复支付"
                    : "订单已过期，请重新购买";
            throw new BusinessException(ErrorCode.OPERATION_ERROR, message);
        }

        ThrowUtils.throwIf(order.getCreateTime() == null, ErrorCode.OPERATION_ERROR, "订单创建时间异常");
        if (System.currentTimeMillis() > order.getCreateTime().getTime() + ORDER_PAY_EXPIRE_MILLIS) {
            boolean orderExpired = commodityOrderService.lambdaUpdate()
                    .set(CommodityOrder::getPayStatus, 2)
                    .eq(CommodityOrder::getId, order.getId())
                    .eq(CommodityOrder::getPayStatus, 0)
                    .eq(CommodityOrder::getIsDelete, 0)
                    .update();
            ThrowUtils.throwIf(!orderExpired, ErrorCode.OPERATION_ERROR, "订单状态更新失败");

            boolean inventoryReleased = lambdaUpdate()
                    .setSql("commodityInventory = IFNULL(commodityInventory, 0) + " + order.getBuyNumber())
                    .eq(Commodity::getId, order.getCommodityId())
                    .eq(Commodity::getIsDelete, 0)
                    .update();
            ThrowUtils.throwIf(!inventoryReleased, ErrorCode.OPERATION_ERROR, "库存释放失败");
            stringRedisTemplate.delete(CACHE_COMMODITY_KEY + order.getCommodityId());
            return false;
        }
        //因为一分钟才检查一次，所以可能在实际过期但未被释放时被支付

        Commodity commodity = getById(order.getCommodityId());
        ThrowUtils.throwIf(commodity == null, ErrorCode.NOT_FOUND_ERROR, "订单商品不存在");

        User user = userService.getByIdWithLock(loginUser.getId());
        //这里也是一样设置悲观锁，防止余额出问题
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");

        BigDecimal balance = user.getBalance() == null ? BigDecimal.ZERO : user.getBalance();
        if (balance.compareTo(order.getPaymentAmount()) < 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "余额不足");
        }

        user.setBalance(balance.subtract(order.getPaymentAmount()));
        boolean userUpdated = userService.updateById(user);
        ThrowUtils.throwIf(!userUpdated, ErrorCode.OPERATION_ERROR, "余额扣减失败");

        boolean orderUpdated = commodityOrderService.lambdaUpdate()
                .set(CommodityOrder::getPayStatus, 1)
                .eq(CommodityOrder::getId, order.getId())
                .eq(CommodityOrder::getPayStatus, 0)
                .eq(CommodityOrder::getIsDelete, 0)
                .update();
        ThrowUtils.throwIf(!orderUpdated, ErrorCode.OPERATION_ERROR, "订单状态更新失败");
//两个悲观锁，一个防止订单重复支付，一个防止用户同时支付多个订单出现负数余额
        return true;
    }

    public void validCommodity(Commodity commodity) {
        Long adminId = commodity.getAdminId();
        String commodityName = commodity.getCommodityName();
        Integer commodityInventory = commodity.getCommodityInventory();
        BigDecimal price = commodity.getPrice();
        if (adminId == null || adminId < 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "操作用户异常");
        }
        ThrowUtils.throwIf(StringUtils.isBlank(commodityName), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf((commodityInventory == null) || (commodityInventory <= 0), ErrorCode.PARAMS_ERROR, "商品数量不符合规则");
        ThrowUtils.throwIf((price == null) || (price.compareTo(BigDecimal.ZERO) <= 0), ErrorCode.PARAMS_ERROR, "商品价格不符合规则");
        ThrowUtils.throwIf(commodity.getCommodityTypeId() == null || commodity.getCommodityTypeId() <= 0, ErrorCode.PARAMS_ERROR, "商品分类不能为空");
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean deleteCommodity(Long id, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Commodity commodity = getById(id);
        ThrowUtils.throwIf(commodity == null, ErrorCode.NOT_FOUND_ERROR);
        if (!userService.isAdmin(request) &&commodity.getAdminId()!=loginUser.getId()){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = removeById(id);
        ThrowUtils.throwIf(result == false, ErrorCode.OPERATION_ERROR);
        stringRedisTemplate.delete(CACHE_COMMODITY_KEY + id);
        stringRedisTemplate.delete(COMMODITY_VIEW_NUM_KEY + id);
        return result;
    }

    @Override
    public Boolean updateCommodity(Commodity commodity) {
        validCommodity(commodity);
        Long id = commodity.getId();
        Commodity oldCommodity = getById(id);
        ThrowUtils.throwIf(oldCommodity == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = updateById(commodity);
        ThrowUtils.throwIf(result == false, ErrorCode.OPERATION_ERROR);
        stringRedisTemplate.delete(CACHE_COMMODITY_KEY + id);
        return result;
    }

    @SneakyThrows
    //这个注解是用来实现自动抛出异常的，主要是jackson本身的objectmapper转换json过程会抛出异常
    @Override
    public CommodityVO getCommodityVOById(Long id, HttpServletRequest request) {
        String commodityJson = stringRedisTemplate.opsForValue().get(CACHE_COMMODITY_KEY + id);
        if (commodityJson != null && commodityJson.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "商品不存在");
        }
        Commodity commodity = null;
        if (commodityJson == null) {
            commodity = getById(id);
            if (commodity == null) {
                stringRedisTemplate.opsForValue().set(CACHE_COMMODITY_KEY + id, "", 1, TimeUnit.MINUTES);
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "商品不存在");
            } else {
                String commodityStr = objectMapper.writeValueAsString(commodity);
                //随机增加0-5分钟防止缓存雪崩
                long ttl = 30L + cn.hutool.core.util.RandomUtil.randomInt(0, 5);
                stringRedisTemplate.opsForValue().set(CACHE_COMMODITY_KEY + id, commodityStr, ttl, TimeUnit.MINUTES);
            }
        } else {
            commodity = objectMapper.readValue(commodityJson, Commodity.class);
        }

        //上方已通过redis获取商品信息，接下来要对浏览量进行单独处理
        //TODO: 原本代码没有对viewnum做处理，这里采取redis存储新增浏览量 + commodity的方式进行存储，定时同步到mysql
        // 这样既能利用redis的缓存也会同步mysql，如果采取简单粗暴的更新mysql浏览量，然后删除缓存，那redis就没用了
        String viewNumKey = COMMODITY_VIEW_NUM_KEY + commodity.getId();
        Long redisViewNum = stringRedisTemplate.opsForValue().increment(viewNumKey);
        int baseViewNum = commodity.getViewNum() == null ? 0 : commodity.getViewNum();
        int addViewNum = (int) (redisViewNum == null ? 0 : redisViewNum);
        commodity.setViewNum(baseViewNum + addViewNum);
        CommodityVO commodityVO = CommodityVO.objToVo(commodity);
        //这里还有两个变量没有赋值需要获取后再赋值
        Long adminId = commodityVO.getAdminId();
        Long commodityTypeId = commodityVO.getCommodityTypeId();
        if (adminId != null) {
            User user = userService.getById(adminId);
            ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "操作用户不存在");
            commodityVO.setAdminName(user.getUserName());
        }
        if (commodityTypeId != null) {
            CommodityType commodityType = commodityTypeService.getCommodityTypeVOById(commodityTypeId);
            ThrowUtils.throwIf(commodityType == null, ErrorCode.NOT_FOUND_ERROR, "商品类型不存在");
            commodityVO.setCommodityTypeName(commodityType.getTypeName());
        }


        return commodityVO;

    }

    private Page<Commodity> listCommodityByPage(CommodityQueryRequest commodityQueryRequest) {
        ThrowUtils.throwIf(commodityQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = commodityQueryRequest.getId();
        String commodityName = commodityQueryRequest.getCommodityName();
        String commodityDescription = commodityQueryRequest.getCommodityDescription();
        String degree = commodityQueryRequest.getDegree();
        Long commodityTypeId = commodityQueryRequest.getCommodityTypeId();
        Long adminId = commodityQueryRequest.getAdminId();
        Integer isListed = commodityQueryRequest.getIsListed();
        Integer commodityInventory = commodityQueryRequest.getCommodityInventory();


        String sortField = commodityQueryRequest.getSortField();
        String sortOrder = commodityQueryRequest.getSortOrder();
        int current = commodityQueryRequest.getCurrent();
        int pageSize = commodityQueryRequest.getPageSize();
        if (current <= 0) current = 1;
        if (pageSize <= 0 || pageSize > 100) pageSize = 10;
        Page<Commodity> page = new Page<>(current, pageSize);
        if (sortField != null && !sortField.trim().isEmpty() && ALLOWED_COMMODITY_SORT_FIELDS.contains(sortField)) {
            if ("asc".equalsIgnoreCase(sortOrder)) {
                page.addOrder(OrderItem.asc(sortField));
            } else {
                page.addOrder(OrderItem.desc(sortField));
            }
        } else {
            // 默认按更新时间降序
            page.addOrder(OrderItem.desc("updateTime"));
        }
        // 模糊查询
        //TODO 貌似前端并没有对所有数据都进行条件查询，有几个变量没有参与查询，至少price都没有，到时候看怎么优化吧
        Page<Commodity> commodityPage = lambdaQuery()
                .like(StringUtils.isNotBlank(commodityName), Commodity::getCommodityName, commodityName)
                .like(StringUtils.isNotBlank(commodityDescription), Commodity::getCommodityDescription, commodityDescription)
                .like(StringUtils.isNotBlank(degree), Commodity::getDegree, degree)
                .eq(ObjectUtils.isNotEmpty(commodityTypeId), Commodity::getCommodityTypeId, commodityTypeId)
                .eq(ObjectUtils.isNotEmpty(id), Commodity::getId, id)
                .eq(ObjectUtils.isNotEmpty(adminId), Commodity::getAdminId, adminId)
                .eq(ObjectUtils.isNotEmpty(commodityInventory), Commodity::getCommodityInventory, commodityInventory)
                .eq(ObjectUtils.isNotEmpty(isListed), Commodity::getIsListed, isListed).page(page);
        return commodityPage;
    }

    @Override
    public Page<CommodityVO> listCommodityVOByPage(CommodityQueryRequest commodityQueryRequest) {
        Page<Commodity> commodityPage = listCommodityByPage(commodityQueryRequest);
        long current = commodityPage.getCurrent();
        long size = commodityPage.getSize();
        long total = commodityPage.getTotal();
        Page<CommodityVO> page = new Page<>(current, size, total);
        List<Commodity> records = commodityPage.getRecords();


        Set<Long> commodityTypeIdList = records.stream().map((commodity) -> {
            Long commodityTypeId = commodity.getCommodityTypeId();
            return commodityTypeId;
        }).collect(Collectors.toSet());
        List<CommodityType> commodityTypes = new ArrayList<>();
        if (!CollUtil.isEmpty(commodityTypeIdList)) {
            commodityTypes = commodityTypeService.listByIds(commodityTypeIdList);
        }
        Map<Long, String> commodityTypeMap = commodityTypes.stream().collect(Collectors.toMap(CommodityType::getId, CommodityType::getTypeName));


        Set<Long> adminIdList = records.stream().map((commodity) -> {
            Long adminId = commodity.getAdminId();
            return adminId;
        }).collect(Collectors.toSet());

        List<User> userList = new ArrayList<>();
        if (!CollUtil.isEmpty(adminIdList)) {
            userList = userService.listByIds(adminIdList);
        }
        Map<Long, String> userMap = userList.stream().collect(Collectors.toMap(User::getId, User::getUserName));


        List<CommodityVO> list = records.stream().map((commodity) -> {
            CommodityVO commodityVO = CommodityVO.objToVo(commodity);
            Long adminId = commodityVO.getAdminId();
            Long commodityTypeId = commodityVO.getCommodityTypeId();
            if (adminId != null) {
                commodityVO.setAdminName(userMap.get(adminId));
            }
            if (commodityTypeId != null) {
                commodityVO.setCommodityTypeName(commodityTypeMap.get(commodityTypeId));
            }
            return commodityVO;
        }).toList();

        page.setRecords(list);
        return page;


    }

    @Override
    public Page<CommodityVO> listMyCommodityVOByPage(CommodityQueryRequest commodityQueryRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        commodityQueryRequest.setAdminId(loginUser.getId());
        Page<CommodityVO> commodityVOPage = listCommodityVOByPage(commodityQueryRequest);
        return commodityVOPage;
    }

    public void validateCommodityExists(Long commodityId) {
        Commodity commodity = getById(commodityId);
        ThrowUtils.throwIf(commodity == null ||
                        Objects.equals(commodity.getIsDelete(), 1) ||
                        Objects.equals(commodity.getIsListed(), 0),
                ErrorCode.NOT_FOUND_ERROR,
                "商品不存在");
    }


    //TODO 关于推荐算法和购买商品还有三个接口尚未实现
}
