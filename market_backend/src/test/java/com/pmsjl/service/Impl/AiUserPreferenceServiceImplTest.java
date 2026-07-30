package com.pmsjl.service.Impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.pmsjl.model.dto.ai.internal.UserPreferenceToolResponse;
import com.pmsjl.model.entity.Commodity;
import com.pmsjl.model.entity.CommodityOrder;
import com.pmsjl.model.entity.CommodityType;
import com.pmsjl.model.entity.UserCommodityFavorites;
import com.pmsjl.model.enums.AiPreferenceConfidenceEnum;
import com.pmsjl.model.enums.AiPreferenceSignalEnum;
import com.pmsjl.service.CommodityOrderService;
import com.pmsjl.service.CommodityService;
import com.pmsjl.service.CommodityTypeService;
import com.pmsjl.service.UserCommodityFavoritesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiUserPreferenceServiceImplTest {

    @Mock
    private CommodityOrderService orderService;
    @Mock
    private UserCommodityFavoritesService favoritesService;
    @Mock
    private CommodityService commodityService;
    @Mock
    private CommodityTypeService typeService;

    @Test
    void buildsDeduplicatedWeightedProfile() {
        when(orderService.list(
                org.mockito.ArgumentMatchers
                        .<Wrapper<CommodityOrder>>any()
        )).thenReturn(List.of(
                order(1L, "44.00", 2, 3000L),
                order(2L, "120.00", 1, 2000L)
        ));
        when(favoritesService.list(
                org.mockito.ArgumentMatchers
                        .<Wrapper<UserCommodityFavorites>>any()
        )).thenReturn(List.of(
                favorite(3L, 4000L),
                favorite(1L, 1000L)
        ));
        when(commodityService.listByIds(anyCollection())).thenReturn(
                List.of(
                        commodity(
                                1L,
                                10L,
                                "Python程序设计基础",
                                "九五新",
                                "22.00"
                        ),
                        commodity(
                                2L,
                                20L,
                                "二手平板",
                                "八五新",
                                "120.00"
                        ),
                        commodity(
                                3L,
                                10L,
                                "算法教材",
                                "九五新",
                                "30.00"
                        )
                )
        );
        when(typeService.listByIds(anyCollection())).thenReturn(
                List.of(
                        type(10L, "教材书籍"),
                        type(20L, "数码产品")
                )
        );

        UserPreferenceToolResponse response = service()
                .buildPreferenceProfile("request-1", 7L);

        assertEquals("request-1", response.getRequestId());
        assertEquals(
                2,
                response.getBehaviorStats().getDistinctPurchaseCount()
        );
        assertEquals(
                1,
                response.getBehaviorStats().getDistinctFavoriteCount()
        );
        assertEquals(
                2,
                response.getBehaviorStats().getDistinctCategoryCount()
        );
        assertEquals(
                AiPreferenceConfidenceEnum.LOW,
                response.getConfidence()
        );
        assertFalse(response.getColdStart());

        assertEquals(10L, response.getPreferredCategories().get(0)
                .getCategoryId());
        assertEquals(
                List.of(
                        AiPreferenceSignalEnum.PURCHASE,
                        AiPreferenceSignalEnum.FAVOUR
                ),
                response.getPreferredCategories().get(0).getSignals()
        );
        assertEquals(
                1D,
                response.getPreferredCategories().get(0).getWeight()
        );
        assertEquals(
                0.75D,
                response.getPreferredCategories().get(1).getWeight()
        );

        assertEquals(
                new BigDecimal("22.00"),
                response.getPurchasePriceProfile().getMinUnitPrice()
        );
        assertEquals(
                new BigDecimal("71.00"),
                response.getPurchasePriceProfile().getMedianUnitPrice()
        );
        assertEquals(
                new BigDecimal("120.00"),
                response.getPurchasePriceProfile().getMaxUnitPrice()
        );
        assertEquals(
                new BigDecimal("30.00"),
                response.getFavoriteCurrentPriceProfile().getMedianPrice()
        );

        assertEquals(
                List.of(3L, 1L, 2L),
                response.getRecentCommodityIds()
        );
        assertEquals(3, response.getRepresentativeInteractions().size());
        assertEquals(
                AiPreferenceSignalEnum.PURCHASE,
                response.getRepresentativeInteractions().stream()
                        .filter(item -> item.getCommodityId().equals(1L))
                        .findFirst()
                        .orElseThrow()
                        .getSignal()
        );
    }

    @Test
    void returnsColdStartWithoutLoadingCommodityTables() {
        when(orderService.list(
                org.mockito.ArgumentMatchers
                        .<Wrapper<CommodityOrder>>any()
        )).thenReturn(List.of());
        when(favoritesService.list(
                org.mockito.ArgumentMatchers
                        .<Wrapper<UserCommodityFavorites>>any()
        )).thenReturn(List.of());

        UserPreferenceToolResponse response = service()
                .buildPreferenceProfile("request-2", 7L);

        assertEquals(AiPreferenceConfidenceEnum.NONE, response.getConfidence());
        assertTrue(response.getColdStart());
        assertTrue(response.getPreferredCategories().isEmpty());
        assertTrue(response.getRepresentativeInteractions().isEmpty());
        assertTrue(response.getRecentCommodityIds().isEmpty());
        assertNull(response.getPurchasePriceProfile());
        assertNull(response.getFavoriteCurrentPriceProfile());
        verify(commodityService, never()).listByIds(anyCollection());
        verify(typeService, never()).listByIds(anyCollection());
    }

    private AiUserPreferenceServiceImpl service() {
        return new AiUserPreferenceServiceImpl(
                orderService,
                favoritesService,
                commodityService,
                typeService
        );
    }

    private CommodityOrder order(
            Long commodityId,
            String paymentAmount,
            int buyNumber,
            long updateTime
    ) {
        CommodityOrder order = new CommodityOrder();
        order.setCommodityId(commodityId);
        order.setPaymentAmount(new BigDecimal(paymentAmount));
        order.setBuyNumber(buyNumber);
        order.setPayStatus(1);
        order.setUpdateTime(new Date(updateTime));
        return order;
    }

    private UserCommodityFavorites favorite(
            Long commodityId,
            long updateTime
    ) {
        UserCommodityFavorites favorite = new UserCommodityFavorites();
        favorite.setCommodityId(commodityId);
        favorite.setStatus(1);
        favorite.setUpdateTime(new Date(updateTime));
        return favorite;
    }

    private Commodity commodity(
            Long id,
            Long typeId,
            String name,
            String degree,
            String price
    ) {
        Commodity commodity = new Commodity();
        commodity.setId(id);
        commodity.setCommodityTypeId(typeId);
        commodity.setCommodityName(name);
        commodity.setCommodityDescription(name + "的公开描述");
        commodity.setDegree(degree);
        commodity.setPrice(new BigDecimal(price));
        return commodity;
    }

    private CommodityType type(Long id, String name) {
        CommodityType type = new CommodityType();
        type.setId(id);
        type.setTypeName(name);
        return type;
    }
}
