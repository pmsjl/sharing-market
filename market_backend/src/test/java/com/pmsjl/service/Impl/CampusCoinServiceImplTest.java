package com.pmsjl.service.Impl;

import com.pmsjl.config.CampusCoinProperties;
import com.pmsjl.mapper.CampusCoinTransactionMapper;
import com.pmsjl.mapper.UserMapper;
import com.pmsjl.model.dto.campusCoin.CampusCoinGrantRequest;
import com.pmsjl.model.entity.CampusCoinTransaction;
import com.pmsjl.model.entity.User;
import com.pmsjl.model.enums.CampusCoinTransactionTypeEnum;
import com.pmsjl.model.vo.CampusCoinWalletVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampusCoinServiceImplTest {
    @Mock
    private CampusCoinTransactionMapper transactionMapper;
    @Mock
    private UserMapper userMapper;

    private CampusCoinServiceImpl service;

    @BeforeEach
    void setUp() {
        CampusCoinProperties properties = new CampusCoinProperties();
        properties.setInitialBalance(new BigDecimal("1000.00"));
        properties.setMaxAdminGrant(new BigDecimal("100000.00"));
        properties.validate();
        service = new CampusCoinServiceImpl(transactionMapper, userMapper, properties);
    }

    @Test
    void successfulPurchaseUpdatesBalanceAndWritesImmutableLedger() {
        User user = user(7L, "100.00");
        when(userMapper.selectByIdForUpdate(7L)).thenReturn(user);
        when(userMapper.updateById(user)).thenReturn(1);
        when(transactionMapper.insert(any(CampusCoinTransaction.class))).thenReturn(1);

        boolean paid = service.tryDebitForPurchase(7L, 88L, new BigDecimal("35.50"));

        assertTrue(paid);
        assertEquals(new BigDecimal("64.50"), user.getBalance());
        ArgumentCaptor<CampusCoinTransaction> captor =
                ArgumentCaptor.forClass(CampusCoinTransaction.class);
        verify(transactionMapper).insert(captor.capture());
        CampusCoinTransaction transaction = captor.getValue();
        assertEquals(7L, transaction.getUserId());
        assertEquals(new BigDecimal("-35.50"), transaction.getAmount());
        assertEquals(new BigDecimal("100.00"), transaction.getBalanceBefore());
        assertEquals(new BigDecimal("64.50"), transaction.getBalanceAfter());
        assertEquals(CampusCoinTransactionTypeEnum.PURCHASE.getValue(), transaction.getTransactionType());
        assertEquals("88", transaction.getBusinessId());
    }

    @Test
    void insufficientBalanceDoesNotUpdateUserOrWriteLedger() {
        User user = user(7L, "20.00");
        when(userMapper.selectByIdForUpdate(7L)).thenReturn(user);

        boolean paid = service.tryDebitForPurchase(7L, 88L, new BigDecimal("35.50"));

        assertFalse(paid);
        assertEquals(new BigDecimal("20.00"), user.getBalance());
        verify(userMapper, never()).updateById(any(User.class));
        verifyNoInteractions(transactionMapper);
    }

    @Test
    void adminGrantUpdatesBalanceAndRecordsOperatorAndReason() {
        User user = user(7L, "100.00");
        when(userMapper.selectByIdForUpdate(7L)).thenReturn(user);
        when(userMapper.updateById(user)).thenReturn(1);
        when(transactionMapper.insert(any(CampusCoinTransaction.class))).thenReturn(1);
        CampusCoinGrantRequest request = new CampusCoinGrantRequest();
        request.setUserId(7L);
        request.setAmount(new BigDecimal("500.00"));
        request.setReason("校园活动奖励");

        CampusCoinWalletVO wallet = service.grantByAdmin(request, 99L);

        assertEquals(new BigDecimal("600.00"), wallet.getBalance());
        ArgumentCaptor<CampusCoinTransaction> captor =
                ArgumentCaptor.forClass(CampusCoinTransaction.class);
        verify(transactionMapper).insert(captor.capture());
        CampusCoinTransaction transaction = captor.getValue();
        assertEquals(new BigDecimal("500.00"), transaction.getAmount());
        assertEquals(CampusCoinTransactionTypeEnum.ADMIN_GRANT.getValue(), transaction.getTransactionType());
        assertEquals(99L, transaction.getOperatorId());
        assertEquals("校园活动奖励", transaction.getRemark());
        assertNotNull(transaction.getBusinessId());
    }

    @Test
    void registrationGrantUpdatesBalanceAndWritesRegistrationLedger() {
        User user = user(7L, "0.00");
        when(userMapper.selectByIdForUpdate(7L)).thenReturn(user);
        when(userMapper.updateById(user)).thenReturn(1);
        when(transactionMapper.insert(any(CampusCoinTransaction.class))).thenReturn(1);

        service.grantForRegistration(7L);

        assertEquals(new BigDecimal("1000.00"), user.getBalance());
        ArgumentCaptor<CampusCoinTransaction> captor =
                ArgumentCaptor.forClass(CampusCoinTransaction.class);
        verify(transactionMapper).insert(captor.capture());
        CampusCoinTransaction transaction = captor.getValue();
        assertEquals(new BigDecimal("1000.00"), transaction.getAmount());
        assertEquals(new BigDecimal("0.00"), transaction.getBalanceBefore());
        assertEquals(new BigDecimal("1000.00"), transaction.getBalanceAfter());
        assertEquals(CampusCoinTransactionTypeEnum.REGISTER_GRANT.getValue(),
                transaction.getTransactionType());
        assertEquals("7", transaction.getBusinessId());
    }

    private static User user(Long id, String balance) {
        User user = new User();
        user.setId(id);
        user.setBalance(new BigDecimal(balance));
        user.setIsDelete(0);
        return user;
    }
}
