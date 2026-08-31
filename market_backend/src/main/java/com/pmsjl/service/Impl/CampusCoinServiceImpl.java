package com.pmsjl.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.common.PageRequest;
import com.pmsjl.config.CampusCoinProperties;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.mapper.CampusCoinTransactionMapper;
import com.pmsjl.mapper.UserMapper;
import com.pmsjl.model.dto.campusCoin.CampusCoinGrantRequest;
import com.pmsjl.model.entity.CampusCoinTransaction;
import com.pmsjl.model.entity.User;
import com.pmsjl.model.enums.CampusCoinTransactionTypeEnum;
import com.pmsjl.model.vo.CampusCoinTransactionVO;
import com.pmsjl.model.vo.CampusCoinWalletVO;
import com.pmsjl.service.CampusCoinService;
import com.pmsjl.utils.ThrowUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class CampusCoinServiceImpl implements CampusCoinService {
    private static final int MAX_REASON_LENGTH = 255;

    private final CampusCoinTransactionMapper campusCoinTransactionMapper;
    private final UserMapper userMapper;
    private final CampusCoinProperties properties;

    @Override
    public CampusCoinWalletVO getMyWallet(Long userId) {
        User user = userMapper.selectById(userId);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        return getWalletVO(balanceOf(user));
    }

    @Override
    public Page<CampusCoinTransactionVO> listMyTransactions(Long userId, PageRequest pageRequest) {
        ThrowUtils.throwIf(pageRequest == null, ErrorCode.PARAMS_ERROR);
        int current = pageRequest.getCurrent() <= 0 ? 1 : pageRequest.getCurrent();
        int pageSize = pageRequest.getPageSize() <= 0 || pageRequest.getPageSize() > 50
                ? 10 : pageRequest.getPageSize();
        Page<CampusCoinTransaction> entityPage = new Page<>(current, pageSize);
        entityPage.addOrder(OrderItem.desc("createTime"), OrderItem.desc("id"));
        entityPage = campusCoinTransactionMapper.selectPage(
                entityPage,
                new LambdaQueryWrapper<CampusCoinTransaction>()
                        .eq(CampusCoinTransaction::getUserId, userId)
        );
        Page<CampusCoinTransactionVO> result = new Page<>(current, pageSize, entityPage.getTotal());
        result.setRecords(entityPage.getRecords().stream().map(item -> {
            CampusCoinTransactionVO vo = new CampusCoinTransactionVO();
            BeanUtil.copyProperties(item, vo);
            return vo;
        }).toList());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampusCoinWalletVO grantByAdmin(CampusCoinGrantRequest request, Long operatorId) {
        ThrowUtils.throwIf(operatorId == null || operatorId <= 0,
                ErrorCode.NO_AUTH_ERROR, "管理员身份无效");
        BigDecimal amount = normalizePositive(request.getAmount(), "发放数量必须大于 0");
        ThrowUtils.throwIf(amount.compareTo(properties.getMaxAdminGrant()) > 0,
                ErrorCode.PARAMS_ERROR, "超过管理员单次发放上限");
        String reason = StringUtils.trimToEmpty(request.getReason());
        ThrowUtils.throwIf(reason.isEmpty() || reason.length() > MAX_REASON_LENGTH,
                ErrorCode.PARAMS_ERROR, "发放原因不能为空且不能超过 255 个字符");

        User user = selectUserForUpdate(request.getUserId());
        BigDecimal before = balanceOf(user);
        BigDecimal after = before.add(amount);
        user.setBalance(after);
        ThrowUtils.throwIf(userMapper.updateById(user) != 1,
                ErrorCode.OPERATION_ERROR, "校园币发放失败");

        Long transactionId = IdWorker.getId();
        //这里手动提前用雪花算法生成id，是因为后续的流水单号businessId用的是同一个值
        insertTransactionWithId(
                transactionId,
                user.getId(),
                amount,
                before,
                after,
                CampusCoinTransactionTypeEnum.ADMIN_GRANT,
                String.valueOf(transactionId),
                operatorId,
                reason
        );
        return getWalletVO(after);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantForRegistration(Long userId) {
        ThrowUtils.throwIf(userId == null || userId <= 0,
                ErrorCode.PARAMS_ERROR, "用户 ID 非法");
        BigDecimal amount = normalizeNonNegative(
                properties.getInitialBalance(), "注册赠送额度非法");
        User user = selectUserForUpdate(userId);
        BigDecimal before = balanceOf(user);
        BigDecimal after = before.add(amount);
        user.setBalance(after);
        ThrowUtils.throwIf(userMapper.updateById(user) != 1,
                ErrorCode.OPERATION_ERROR, "注册赠送校园币失败");
        insertTransaction(
                userId,
                amount,
                before,
                after,
                CampusCoinTransactionTypeEnum.REGISTER_GRANT,
                String.valueOf(userId),
                null,
                "新用户注册赠送校园币"
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean tryDebitForPurchase(Long userId, Long orderId, BigDecimal amount) {
        validatePurchaseArguments(userId, orderId);
        BigDecimal normalized = normalizePositive(amount, "订单支付金额必须大于 0");
        User user = selectUserForUpdate(userId);
        BigDecimal before = balanceOf(user);
        if (before.compareTo(normalized) < 0) {
            return false;
        }
        BigDecimal after = before.subtract(normalized);
        user.setBalance(after);
        ThrowUtils.throwIf(userMapper.updateById(user) != 1,
                ErrorCode.OPERATION_ERROR, "校园币扣减失败");
        insertTransaction(
                userId,
                normalized.negate(),
                before,
                after,
                CampusCoinTransactionTypeEnum.PURCHASE,
                String.valueOf(orderId),
                null,
                "商品订单支付"
        );
        return true;
    }

    @Override
    public void debitForPurchase(Long userId, Long orderId, BigDecimal amount) {
        if (!tryDebitForPurchase(userId, orderId, amount)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "校园币不足");
        }
    }

    private User selectUserForUpdate(Long userId) {
        User user = userMapper.selectByIdForUpdate(userId);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        return user;
    }

    private void validatePurchaseArguments(Long userId, Long orderId) {
        ThrowUtils.throwIf(userId == null || userId <= 0 || orderId == null || orderId <= 0,
                ErrorCode.PARAMS_ERROR, "校园币支付业务标识非法");
    }

    private BigDecimal balanceOf(User user) {
        return user.getBalance() == null
                ? BigDecimal.ZERO.setScale(2)
                : user.getBalance().setScale(2, RoundingMode.UNNECESSARY);
    }

    private BigDecimal normalizePositive(BigDecimal amount, String message) {
        ThrowUtils.throwIf(amount == null || amount.compareTo(BigDecimal.ZERO) < 0,
                ErrorCode.PARAMS_ERROR, message);
        try {
            BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
            ThrowUtils.throwIf(normalized.compareTo(BigDecimal.ZERO) <= 0, ErrorCode.PARAMS_ERROR, message);
            return normalized;
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "校园币最多保留两位小数");
        }
    }

    private BigDecimal normalizeNonNegative(BigDecimal amount, String message) {
        ThrowUtils.throwIf(amount == null || amount.compareTo(BigDecimal.ZERO) < 0,
                ErrorCode.PARAMS_ERROR, message);
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "校园币最多保留两位小数");
        }
    }


    private CampusCoinWalletVO getWalletVO(BigDecimal balance) {
        CampusCoinWalletVO vo = new CampusCoinWalletVO();
        vo.setBalance(balance);
        return vo;
    }

    private void insertTransaction(Long userId,
                                   BigDecimal amount,
                                   BigDecimal before,
                                   BigDecimal after,
                                   CampusCoinTransactionTypeEnum type,
                                   String businessId,
                                   Long operatorId,
                                   String remark) {
        insertTransactionWithId(null, userId, amount, before, after, type,
                businessId, operatorId, remark);
    }

    private void insertTransactionWithId(Long id,
                                         Long userId,
                                         BigDecimal amount,
                                         BigDecimal before,
                                         BigDecimal after,
                                         CampusCoinTransactionTypeEnum type,
                                         String businessId,
                                         Long operatorId,
                                         String remark) {
        CampusCoinTransaction transaction = new CampusCoinTransaction();
        transaction.setId(id);
        transaction.setUserId(userId);
        transaction.setAmount(amount);
        transaction.setBalanceBefore(before);
        transaction.setBalanceAfter(after);
        transaction.setTransactionType(type.getValue());
        transaction.setBusinessId(businessId);
        transaction.setOperatorId(operatorId);
        transaction.setRemark(remark);
        transaction.setCreateTime(new Date());
        ThrowUtils.throwIf(campusCoinTransactionMapper.insert(transaction) != 1,
                ErrorCode.OPERATION_ERROR, "校园币流水写入失败");
    }
}
