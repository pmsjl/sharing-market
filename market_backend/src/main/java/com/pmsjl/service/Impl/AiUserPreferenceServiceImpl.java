package com.pmsjl.service.Impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pmsjl.model.dto.ai.internal.UserPreferenceToolResponse;
import com.pmsjl.model.entity.Commodity;
import com.pmsjl.model.entity.CommodityOrder;
import com.pmsjl.model.entity.CommodityType;
import com.pmsjl.model.entity.UserCommodityFavorites;
import com.pmsjl.model.enums.AiPreferenceConfidenceEnum;
import com.pmsjl.model.enums.AiPreferenceSignalEnum;
import com.pmsjl.service.AiUserPreferenceService;
import com.pmsjl.service.CommodityOrderService;
import com.pmsjl.service.CommodityService;
import com.pmsjl.service.CommodityTypeService;
import com.pmsjl.service.UserCommodityFavoritesService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AiUserPreferenceServiceImpl
        implements AiUserPreferenceService {

    private static final int PURCHASE_SCORE = 3;
    private static final int FAVOUR_SCORE = 1;

    private final CommodityOrderService commodityOrderService;
    private final UserCommodityFavoritesService favoritesService;
    private final CommodityService commodityService;
    private final CommodityTypeService commodityTypeService;

    public AiUserPreferenceServiceImpl(
            CommodityOrderService commodityOrderService,
            UserCommodityFavoritesService favoritesService,
            CommodityService commodityService,
            CommodityTypeService commodityTypeService
    ) {
        this.commodityOrderService = commodityOrderService;
        this.favoritesService = favoritesService;
        this.commodityService = commodityService;
        this.commodityTypeService = commodityTypeService;
    }

    @Override
    public UserPreferenceToolResponse buildPreferenceProfile(
            String requestId,
            Long userId
    ) {
        List<CommodityOrder> paidOrders = loadLatestPaidOrders(userId);
        List<UserCommodityFavorites> activeFavorites =
                loadLatestActiveFavorites(userId);

        Set<Long> purchasedCommodityIds = paidOrders.stream()
                .map(CommodityOrder::getCommodityId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        activeFavorites.removeIf(
                favorite -> purchasedCommodityIds.contains(
                        favorite.getCommodityId()
                )
        );

        Set<Long> behaviorCommodityIds = new LinkedHashSet<>();
        //LinkedHashSet不是乱序！
        //它相当于是去重的list，插入是保留原有顺序的！hashset是乱序
        paidOrders.stream()
                .map(CommodityOrder::getCommodityId)
                .filter(Objects::nonNull)
                .forEach(behaviorCommodityIds::add);
        //这个和上面的purchasedCommodityIds区别就是这个是保留顺序的

        activeFavorites.stream()
                .map(UserCommodityFavorites::getCommodityId)
                .filter(Objects::nonNull)
                .forEach(behaviorCommodityIds::add);

        Map<Long, Commodity> commodityMap =
                loadCommodityMap(behaviorCommodityIds);
        Map<Long, CommodityType> typeMap =
                loadCommodityTypeMap(commodityMap.values());

        List<EffectiveBehavior> behaviors = new ArrayList<>();
        for (CommodityOrder order : paidOrders) {
            addPurchaseBehavior(behaviors, order, commodityMap, typeMap);
        }
        for (UserCommodityFavorites favorite : activeFavorites) {
            addFavoriteBehavior(behaviors, favorite, commodityMap, typeMap);
        }

        return buildResponse(requestId, behaviors);
    }

    private List<CommodityOrder> loadLatestPaidOrders(Long userId) {
        List<CommodityOrder> orders = commodityOrderService.lambdaQuery()
                .eq(CommodityOrder::getUserId, userId)
                .eq(CommodityOrder::getPayStatus, 1)
                .orderByDesc(CommodityOrder::getUpdateTime)
                .orderByDesc(CommodityOrder::getId).list();
        orders.sort(
                Comparator.comparing(
                        this::eventTime,
                        Comparator.reverseOrder()
                )
        );

        Map<Long, CommodityOrder> latestByCommodity =
                new LinkedHashMap<>();
        for (CommodityOrder order : orders) {
            if (order.getCommodityId() != null) {
                latestByCommodity.putIfAbsent(
                        order.getCommodityId(),
                        order
                );
            }
        }
        return new ArrayList<>(latestByCommodity.values());
    }

    private List<UserCommodityFavorites> loadLatestActiveFavorites(
            Long userId
    ) {
        List<UserCommodityFavorites> favorites =
                favoritesService.lambdaQuery()
                        .eq(UserCommodityFavorites::getUserId, userId)
                        .eq(UserCommodityFavorites::getStatus, 1)
                        .orderByDesc(
                                UserCommodityFavorites::getUpdateTime
                        )
                        .orderByDesc(UserCommodityFavorites::getId).list();


        favorites.sort(
                Comparator.comparing(
                        this::eventTime,
                        Comparator.reverseOrder()
                )
        );

        return favorites;
    }

    private Map<Long, Commodity> loadCommodityMap(
            Set<Long> commodityIds
    ) {
        if (commodityIds.isEmpty()) {
            return Map.of();
        }
        return commodityService.listByIds(commodityIds).stream()
                .filter(this::hasRequiredCommodityFields)
                .collect(Collectors.toMap(
                        Commodity::getId,
                        commodity -> commodity
                ));
    }

    private Map<Long, CommodityType> loadCommodityTypeMap(
            java.util.Collection<Commodity> commodities
    ) {
        Set<Long> typeIds = commodities.stream()
                .map(Commodity::getCommodityTypeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (typeIds.isEmpty()) {
            return Map.of();
        }
        return commodityTypeService.listByIds(typeIds).stream()
                .filter(type -> type.getId() != null)
                .filter(type -> hasText(type.getTypeName()))
                .collect(Collectors.toMap(
                        CommodityType::getId,
                        commodityType -> commodityType
                ));
    }

    private void addPurchaseBehavior(
            List<EffectiveBehavior> behaviors,
            CommodityOrder order,
            Map<Long, Commodity> commodityMap,
            Map<Long, CommodityType> typeMap
    ) {
        Commodity commodity = commodityMap.get(order.getCommodityId());
        CommodityType type = resolvedType(commodity, typeMap);
        if (commodity == null || type == null) {
            return;
        }
        behaviors.add(new EffectiveBehavior(
                commodity,
                type,
                AiPreferenceSignalEnum.PURCHASE,
                eventTime(order),
                order
        ));
    }

    private void addFavoriteBehavior(
            List<EffectiveBehavior> behaviors,
            UserCommodityFavorites favorite,
            Map<Long, Commodity> commodityMap,
            Map<Long, CommodityType> typeMap
    ) {
        Commodity commodity = commodityMap.get(favorite.getCommodityId());
        CommodityType type = resolvedType(commodity, typeMap);
        if (commodity == null || type == null) {
            return;
        }
        behaviors.add(new EffectiveBehavior(
                commodity,
                type,
                AiPreferenceSignalEnum.FAVOUR,
                eventTime(favorite),
                null
        ));
    }

    private CommodityType resolvedType(
            Commodity commodity,
            Map<Long, CommodityType> typeMap
    ) {
        if (commodity == null || commodity.getCommodityTypeId() == null) {
            return null;
        }
        return typeMap.get(commodity.getCommodityTypeId());
    }

    private UserPreferenceToolResponse buildResponse(
            String requestId,
            List<EffectiveBehavior> behaviors
    ) {
        UserPreferenceToolResponse response =
                new UserPreferenceToolResponse();
        response.setRequestId(requestId);

        long purchaseCount = behaviors.stream()
                .filter(this::isPurchase)
                .count();
        long favoriteCount = behaviors.size() - purchaseCount;
        long categoryCount = behaviors.stream()
                .map(behavior -> behavior.type().getId())
                .distinct()
                .count();

        UserPreferenceToolResponse.BehaviorStats stats =
                new UserPreferenceToolResponse.BehaviorStats();
        stats.setDistinctPurchaseCount((int) purchaseCount);
        stats.setDistinctFavoriteCount((int) favoriteCount);
        stats.setDistinctCategoryCount((int) categoryCount);
        response.setBehaviorStats(stats);

        response.setPreferredCategories(buildCategories(behaviors));
        response.setRepresentativeInteractions(
                buildRepresentativeInteractions(behaviors)
        );
        response.setPurchasePriceProfile(
                buildPurchasePriceProfile(behaviors)
        );
        response.setFavoriteCurrentPriceProfile(
                buildFavoritePriceProfile(behaviors)
        );
        response.setPreferredDegrees(buildPreferredDegrees(behaviors));
        response.setRecentCommodityIds(
                behaviors.stream()
                        .sorted(this::compareByRecent)
                        .map(behavior -> behavior.commodity().getId())
                        .distinct()
                        .limit(20)
                        .toList()
        );

        AiPreferenceConfidenceEnum confidence =
                confidenceFor(behaviors.size());
        response.setConfidence(confidence);
        response.setColdStart(
                confidence == AiPreferenceConfidenceEnum.NONE
        );
        return response;
    }

    private List<UserPreferenceToolResponse.PreferredCategory>
    buildCategories(List<EffectiveBehavior> behaviors) {
        Map<Long, PreferenceAccumulator> accumulators =
                new LinkedHashMap<>();
        for (EffectiveBehavior behavior : behaviors) {
            PreferenceAccumulator accumulator =
                    accumulators.computeIfAbsent(
                            behavior.type().getId(),
                            ignored -> new PreferenceAccumulator(
                                    behavior.type().getTypeName()
                            )
                    );
            accumulator.add(behavior.signal());
        }

        int maximumScore = accumulators.values().stream()
                .mapToInt(PreferenceAccumulator::score)
                .max()
                .orElse(0);
        return accumulators.entrySet().stream()
                .sorted(
                        Comparator
                                .<Map.Entry<Long, PreferenceAccumulator>>
                                        comparingInt(
                                        entry -> entry.getValue()
                                                .score()
                                )
                                .reversed()
                                .thenComparing(Map.Entry::getKey)
                )
                .limit(10)
                .map(entry -> {
                    UserPreferenceToolResponse.PreferredCategory item =
                            new UserPreferenceToolResponse
                                    .PreferredCategory();
                    item.setCategoryId(entry.getKey());
                    item.setCategoryName(entry.getValue().label());
                    item.setWeight(
                            normalizedWeight(
                                    entry.getValue().score(),
                                    maximumScore
                            )
                    );
                    item.setSignals(entry.getValue().signals());
                    item.setEvidence(entry.getValue().evidence());
                    return item;
                })
                .toList();
    }

    private List<UserPreferenceToolResponse.RepresentativeInteraction>
    buildRepresentativeInteractions(
            List<EffectiveBehavior> behaviors
    ) {
        List<EffectiveBehavior> purchases = behaviors.stream()
                .filter(this::isPurchase)
                .sorted(this::compareByRecent)
                .toList();
        List<EffectiveBehavior> favorites = behaviors.stream()
                .filter(behavior -> !isPurchase(behavior))
                .sorted(this::compareByRecent)
                .toList();

        List<EffectiveBehavior> selected = new ArrayList<>();
        purchases.stream().limit(4).forEach(selected::add);
        favorites.stream().limit(4).forEach(selected::add);

        if (selected.size() < 8) {
            Set<Long> selectedIds = selected.stream()
                    .map(item -> item.commodity().getId())
                    .collect(Collectors.toSet());
            behaviors.stream()
                    .sorted(this::compareByRecent)
                    .filter(
                            item -> !selectedIds.contains(
                                    item.commodity().getId()
                            )
                    )
                    .limit(8 - selected.size())
                    .forEach(selected::add);
        }

        return selected.stream()
                .sorted(this::compareByRecent)
                .limit(8)
                .map(this::toRepresentativeInteraction)
                .toList();
    }

    private UserPreferenceToolResponse.RepresentativeInteraction
    toRepresentativeInteraction(EffectiveBehavior behavior) {
        UserPreferenceToolResponse.RepresentativeInteraction item =
                new UserPreferenceToolResponse
                        .RepresentativeInteraction();
        item.setCommodityId(behavior.commodity().getId());
        item.setCommodityName(behavior.commodity().getCommodityName());
        item.setDescriptionSnippet(
                snippet(behavior.commodity().getCommodityDescription())
        );
        item.setCategoryId(behavior.type().getId());
        item.setCategoryName(behavior.type().getTypeName());
        item.setDegree(blankToNull(behavior.commodity().getDegree()));
        item.setSignal(behavior.signal());
        return item;
    }

    private UserPreferenceToolResponse.PurchasePriceProfile
    buildPurchasePriceProfile(List<EffectiveBehavior> behaviors) {
        List<BigDecimal> prices = behaviors.stream()
                .filter(this::isPurchase)
                .map(this::unitPurchasePrice)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        if (prices.isEmpty()) {
            return null;
        }

        UserPreferenceToolResponse.PurchasePriceProfile profile =
                new UserPreferenceToolResponse.PurchasePriceProfile();
        profile.setSampleCount(prices.size());
        profile.setMinUnitPrice(prices.get(0));
        profile.setMedianUnitPrice(median(prices));
        profile.setMaxUnitPrice(prices.get(prices.size() - 1));
        return profile;
    }

    private UserPreferenceToolResponse.FavoriteCurrentPriceProfile
    buildFavoritePriceProfile(List<EffectiveBehavior> behaviors) {
        List<BigDecimal> prices = behaviors.stream()
                .filter(behavior -> !isPurchase(behavior))
                .map(behavior -> behavior.commodity().getPrice())
                .filter(Objects::nonNull)
                .filter(price -> price.signum() >= 0)
                .map(price -> price.setScale(2, RoundingMode.HALF_UP))
                .sorted()
                .toList();
        if (prices.isEmpty()) {
            return null;
        }

        UserPreferenceToolResponse.FavoriteCurrentPriceProfile profile =
                new UserPreferenceToolResponse
                        .FavoriteCurrentPriceProfile();
        profile.setSampleCount(prices.size());
        profile.setMinPrice(prices.get(0));
        profile.setMedianPrice(median(prices));
        profile.setMaxPrice(prices.get(prices.size() - 1));
        return profile;
    }

    private List<UserPreferenceToolResponse.PreferredDegree>
    buildPreferredDegrees(List<EffectiveBehavior> behaviors) {
        Map<String, PreferenceAccumulator> accumulators =
                new LinkedHashMap<>();
        for (EffectiveBehavior behavior : behaviors) {
            String degree = blankToNull(
                    behavior.commodity().getDegree()
            );
            if (degree == null) {
                continue;
            }
            accumulators.computeIfAbsent(
                    degree,
                    PreferenceAccumulator::new
            ).add(behavior.signal());
        }

        int maximumScore = accumulators.values().stream()
                .mapToInt(PreferenceAccumulator::score)
                .max()
                .orElse(0);
        return accumulators.entrySet().stream()
                .sorted(
                        Comparator
                                .<Map.Entry<String, PreferenceAccumulator>>
                                        comparingInt(
                                        entry -> entry.getValue()
                                                .score()
                                )
                                .reversed()
                                .thenComparing(Map.Entry::getKey)
                )
                .limit(5)
                .map(entry -> {
                    UserPreferenceToolResponse.PreferredDegree item =
                            new UserPreferenceToolResponse
                                    .PreferredDegree();
                    item.setDegree(entry.getKey());
                    item.setWeight(
                            normalizedWeight(
                                    entry.getValue().score(),
                                    maximumScore
                            )
                    );
                    item.setEvidence(entry.getValue().evidence());
                    return item;
                })
                .toList();
    }

    private BigDecimal unitPurchasePrice(EffectiveBehavior behavior) {
        CommodityOrder order = behavior.order();
        if (order == null
                || order.getPaymentAmount() == null
                || order.getPaymentAmount().signum() < 0
                || order.getBuyNumber() == null
                || order.getBuyNumber() <= 0) {
            return null;
        }
        return order.getPaymentAmount().divide(
                BigDecimal.valueOf(order.getBuyNumber()),
                2,
                RoundingMode.HALF_UP
        );
    }

    private BigDecimal median(List<BigDecimal> sortedPrices) {
        int size = sortedPrices.size();
        int middle = size / 2;
        if (size % 2 == 1) {
            return sortedPrices.get(middle);
        }
        return sortedPrices.get(middle - 1)
                .add(sortedPrices.get(middle))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private AiPreferenceConfidenceEnum confidenceFor(int sampleCount) {
        if (sampleCount == 0) {
            return AiPreferenceConfidenceEnum.NONE;
        }
        if (sampleCount <= 3) {
            return AiPreferenceConfidenceEnum.LOW;
        }
        if (sampleCount <= 6) {
            return AiPreferenceConfidenceEnum.MEDIUM;
        }
        return AiPreferenceConfidenceEnum.HIGH;
    }

    private boolean isPurchase(EffectiveBehavior behavior) {
        return behavior.signal() == AiPreferenceSignalEnum.PURCHASE;
    }

    private int compareByRecent(
            EffectiveBehavior left,
            EffectiveBehavior right
    ) {
        return right.eventTime().compareTo(left.eventTime());
    }

    private Date eventTime(CommodityOrder order) {
        return firstNonNull(
                order.getUpdateTime(),
                order.getCreateTime()
        );
    }

    private Date eventTime(UserCommodityFavorites favorite) {
        return firstNonNull(
                favorite.getUpdateTime(),
                favorite.getCreateTime()
        );
    }

    private Date firstNonNull(Date primary, Date fallback) {
        if (primary != null) {
            return primary;
        }
        return fallback == null ? new Date(0) : fallback;
    }

    private boolean hasRequiredCommodityFields(Commodity commodity) {
        return commodity.getId() != null
                && commodity.getCommodityTypeId() != null
                && hasText(commodity.getCommodityName());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String snippet(String description) {
        String normalized = blankToNull(description);
        if (normalized == null || normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 120);
    }

    private double normalizedWeight(int score, int maximumScore) {
        if (maximumScore <= 0) {
            return 0D;
        }
        return Math.round(
                (double) score / maximumScore * 10_000D
        ) / 10_000D;
    }

    private record EffectiveBehavior(
            Commodity commodity,
            CommodityType type,
            AiPreferenceSignalEnum signal,
            Date eventTime,
            CommodityOrder order
    ) {
    }

    private static class PreferenceAccumulator {
        private final String label;
        private int paidPurchaseCount;
        private int activeFavoriteCount;
        private final EnumSet<AiPreferenceSignalEnum> signals =
                EnumSet.noneOf(AiPreferenceSignalEnum.class);

        private PreferenceAccumulator(String label) {
            this.label = label;
        }

        private void add(AiPreferenceSignalEnum signal) {
            signals.add(signal);
            if (signal == AiPreferenceSignalEnum.PURCHASE) {
                paidPurchaseCount++;
            } else if (signal == AiPreferenceSignalEnum.FAVOUR) {
                activeFavoriteCount++;
            }
        }

        private int score() {
            return paidPurchaseCount * PURCHASE_SCORE
                    + activeFavoriteCount * FAVOUR_SCORE;
        }

        private String label() {
            return label;
        }

        private List<AiPreferenceSignalEnum> signals() {
            List<AiPreferenceSignalEnum> result = new ArrayList<>();
            if (signals.contains(AiPreferenceSignalEnum.PURCHASE)) {
                result.add(AiPreferenceSignalEnum.PURCHASE);
            }
            if (signals.contains(AiPreferenceSignalEnum.FAVOUR)) {
                result.add(AiPreferenceSignalEnum.FAVOUR);
            }
            return result;
        }

        private UserPreferenceToolResponse.PreferenceEvidence evidence() {
            UserPreferenceToolResponse.PreferenceEvidence evidence =
                    new UserPreferenceToolResponse.PreferenceEvidence();
            evidence.setPaidPurchaseCount(paidPurchaseCount);
            evidence.setActiveFavoriteCount(activeFavoriteCount);
            return evidence;
        }
    }
}
