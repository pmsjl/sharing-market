package com.pmsjl.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.common.PageRequest;
import com.pmsjl.model.dto.campusCoin.CampusCoinGrantRequest;
import com.pmsjl.model.vo.CampusCoinTransactionVO;
import com.pmsjl.model.vo.CampusCoinWalletVO;

import java.math.BigDecimal;

public interface CampusCoinService {
    CampusCoinWalletVO getMyWallet(Long userId);

    Page<CampusCoinTransactionVO> listMyTransactions(Long userId, PageRequest pageRequest);

    CampusCoinWalletVO grantByAdmin(CampusCoinGrantRequest request, Long operatorId);

    void grantForRegistration(Long userId);

    boolean tryDebitForPurchase(Long userId, Long orderId, BigDecimal amount);

    void debitForPurchase(Long userId, Long orderId, BigDecimal amount);
}
