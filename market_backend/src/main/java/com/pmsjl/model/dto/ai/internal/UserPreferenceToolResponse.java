package com.pmsjl.model.dto.ai.internal;

import com.pmsjl.model.enums.AiPreferenceConfidenceEnum;
import com.pmsjl.model.enums.AiPreferenceSignalEnum;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Privacy-limited preference profile exposed to the Python Agent. */
@Data
public class UserPreferenceToolResponse implements Serializable {

    private String requestId;
    private BehaviorStats behaviorStats = new BehaviorStats();
    private List<PreferredCategory> preferredCategories = new ArrayList<>();
    private List<RepresentativeInteraction> representativeInteractions =
            new ArrayList<>();
    private PurchasePriceProfile purchasePriceProfile;
    private FavoriteCurrentPriceProfile favoriteCurrentPriceProfile;
    private List<PreferredDegree> preferredDegrees = new ArrayList<>();
    private List<Long> recentCommodityIds = new ArrayList<>();
    private AiPreferenceConfidenceEnum confidence =
            AiPreferenceConfidenceEnum.NONE;
    private Boolean coldStart = true;

    @Data
    public static class BehaviorStats implements Serializable {
        private Integer distinctPurchaseCount = 0;
        private Integer distinctFavoriteCount = 0;
        private Integer distinctCategoryCount = 0;
    }

    @Data
    public static class PreferenceEvidence implements Serializable {
        private Integer paidPurchaseCount = 0;
        private Integer activeFavoriteCount = 0;
    }

    @Data
    public static class PreferredCategory implements Serializable {
        private Long categoryId;
        private String categoryName;
        private Double weight;
        private List<AiPreferenceSignalEnum> signals = new ArrayList<>();
        private PreferenceEvidence evidence = new PreferenceEvidence();
    }

    @Data
    public static class RepresentativeInteraction implements Serializable {
        private Long commodityId;
        private String commodityName;
        private String descriptionSnippet;
        private Long categoryId;
        private String categoryName;
        private String degree;
        private AiPreferenceSignalEnum signal;
    }

    @Data
    public static class PurchasePriceProfile implements Serializable {
        private Integer sampleCount;
        private BigDecimal minUnitPrice;
        private BigDecimal medianUnitPrice;
        private BigDecimal maxUnitPrice;
    }

    @Data
    public static class FavoriteCurrentPriceProfile implements Serializable {
        private Integer sampleCount;
        private BigDecimal minPrice;
        private BigDecimal medianPrice;
        private BigDecimal maxPrice;
    }

    @Data
    public static class PreferredDegree implements Serializable {
        private String degree;
        private Double weight;
        private PreferenceEvidence evidence = new PreferenceEvidence();
    }

    private static final long serialVersionUID = 1L;
}
