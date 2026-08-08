package com.pmsjl.controller;

import com.pmsjl.common.Result;
import com.pmsjl.service.CommodityScoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommodityScoreControllerTest {

    @Mock
    private CommodityScoreService commodityScoreService;

    private CommodityScoreController controller;

    @BeforeEach
    void setUp() {
        controller = new CommodityScoreController();
        ReflectionTestUtils.setField(
                controller,
                "commodityScoreService",
                commodityScoreService
        );
    }

    @Test
    void getAverageScoreReturnsZeroWhenCommodityHasNoRatings() {
        when(commodityScoreService.getAverageScoreById(7L)).thenReturn(null);

        Result<Double> result = controller.getAverageScore(7L);

        assertEquals(200, result.getCode());
        assertEquals(0.0D, result.getData());
    }

    @Test
    void getAverageScoreReturnsCalculatedValue() {
        when(commodityScoreService.getAverageScoreById(7L)).thenReturn(4.5D);

        Result<Double> result = controller.getAverageScore(7L);

        assertEquals(200, result.getCode());
        assertEquals(4.5D, result.getData());
    }
}
