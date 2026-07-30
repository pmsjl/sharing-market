from decimal import Decimal
from enum import Enum

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    field_validator,
    model_validator,
)


#SEARCH_COMMODITIES_TOOL对应的类
class CommoditySort(str, Enum):
    RELEVANCE = "RELEVANCE"
    PRICE_ASC = "PRICE_ASC"
    PRICE_DESC = "PRICE_DESC"
    FAVOUR_DESC = "FAVOUR_DESC"


class CommoditySearchArguments(BaseModel):
    model_config = ConfigDict(extra="forbid")

    keywords: list[str] = Field(default_factory=list, max_length=5)
    categoryIds: list[int] = Field(default_factory=list, max_length=10)
    minPrice: float | None = Field(default=None, ge=0)
    maxPrice: float | None = Field(default=None, ge=0)
    degrees: list[str] = Field(default_factory=list, max_length=10)
    excludeCommodityIds: list[int] = Field(default_factory=list, max_length=20)
    sortBy: CommoditySort = CommoditySort.RELEVANCE
    limit: int = Field(default=10, ge=1, le=20)

    @field_validator("keywords")
    @classmethod
    def normalize_keywords(cls, keywords: list[str]) -> list[str]:
        normalized_keywords: list[str] = []
        seen: set[str] = set()

        for keyword in keywords:
            normalized_keyword = keyword.strip()
            if not normalized_keyword:
                raise ValueError("keywords不能包含空字符串")
            if len(normalized_keyword) > 30:
                raise ValueError("单个keyword不能超过30个字符")
            if normalized_keyword not in seen:
                seen.add(normalized_keyword)
                normalized_keywords.append(normalized_keyword)

        return normalized_keywords

    @model_validator(mode="after")
    def validate_price_range(self):
        if (self.minPrice is not None and self.maxPrice is not None
                and self.maxPrice < self.minPrice):
            raise ValueError("maxPrice不能小于minPrice")

        return self

    """
     这里的方法就是在类实例化后自动触发进行检测
    """


class AiCommoditySearchItem(BaseModel):
    id: str

    commodityName: str

    commodityDescription: str | None = None

    degree: str | None = None

    commodityTypeName: str | None = None

    commodityInventory: int = Field(ge=0)

    price: Decimal


class CommoditySearchToolResponse(BaseModel):
    requestId: str
    matchedCount: int
    items: list[AiCommoditySearchItem] = Field(default_factory=list)


#GET_MY_PREFERENCE_SIGNALS_TOOL涉及到的类
class PreferenceSignal(str, Enum):
    PURCHASE = "PURCHASE"
    FAVOUR = "FAVOUR"


class PreferenceConfidence(str, Enum):
    NONE = "NONE"
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"


class PreferenceToolArguments(BaseModel):
    model_config = ConfigDict(extra="forbid")


class PreferenceEvidence(BaseModel):
    model_config = ConfigDict(extra="forbid")

    paidPurchaseCount: int = Field(ge=0)
    activeFavoriteCount: int = Field(ge=0)


class PreferredCategory(BaseModel):
    model_config = ConfigDict(extra="forbid")

    categoryId: str = Field(
        min_length=1,
        max_length=30,
        pattern=r"^\d+$",
    )
    categoryName: str = Field(
        min_length=1,
        max_length=100,
    )
    weight: float = Field(
        ge=0,
        le=1,
    )
    signals: list[PreferenceSignal] = Field(max_length=2, )
    evidence: PreferenceEvidence


class RepresentativeInteraction(BaseModel):
    model_config = ConfigDict(extra="forbid")

    commodityId: str = Field(
        min_length=1,
        max_length=30,
        pattern=r"^\d+$",
    )
    commodityName: str = Field(
        min_length=1,
        max_length=200,
    )
    descriptionSnippet: str | None = Field(max_length=120, )
    categoryId: str = Field(
        min_length=1,
        max_length=30,
        pattern=r"^\d+$",
    )
    categoryName: str = Field(
        min_length=1,
        max_length=100,
    )
    degree: str | None = Field(max_length=50, )
    signal: PreferenceSignal


class PurchasePriceProfile(BaseModel):
    model_config = ConfigDict(extra="forbid")

    sampleCount: int = Field(ge=1)
    minUnitPrice: Decimal = Field(ge=0)
    medianUnitPrice: Decimal = Field(ge=0)
    maxUnitPrice: Decimal = Field(ge=0)

    @model_validator(mode="after")
    def validate_price_order(self):
        if not (self.minUnitPrice <= self.medianUnitPrice <=
                self.maxUnitPrice):
            raise ValueError("购买价格画像顺序不合法")
        return self


class FavoriteCurrentPriceProfile(BaseModel):
    model_config = ConfigDict(extra="forbid")

    sampleCount: int = Field(ge=1)
    minPrice: Decimal = Field(ge=0)
    medianPrice: Decimal = Field(ge=0)
    maxPrice: Decimal = Field(ge=0)

    @model_validator(mode="after")
    def validate_price_order(self):
        if not self.minPrice <= self.medianPrice <= self.maxPrice:
            raise ValueError("收藏商品当前价格画像顺序不合法")
        return self


class PreferredDegree(BaseModel):
    model_config = ConfigDict(extra="forbid")

    degree: str = Field(
        min_length=1,
        max_length=50,
    )
    weight: float = Field(
        ge=0,
        le=1,
    )
    evidence: PreferenceEvidence


class PreferenceBehaviorStats(BaseModel):
    model_config = ConfigDict(extra="forbid")

    distinctPurchaseCount: int = Field(ge=0)
    distinctFavoriteCount: int = Field(ge=0)
    distinctCategoryCount: int = Field(ge=0)


class UserPreferenceToolResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    requestId: str

    behaviorStats: PreferenceBehaviorStats

    preferredCategories: list[PreferredCategory] = Field(max_length=10, )

    representativeInteractions: list[RepresentativeInteraction] = Field(
        max_length=8, )

    purchasePriceProfile: PurchasePriceProfile | None

    favoriteCurrentPriceProfile: (FavoriteCurrentPriceProfile | None)

    preferredDegrees: list[PreferredDegree] = Field(max_length=5, )

    recentCommodityIds: list[str] = Field(max_length=20, )

    confidence: PreferenceConfidence

    coldStart: bool

    @model_validator(mode="after")
    def validate_profile_consistency(self):
        effective_count = (self.behaviorStats.distinctPurchaseCount +
                           self.behaviorStats.distinctFavoriteCount)

        if effective_count == 0:
            expected_confidence = PreferenceConfidence.NONE
        elif effective_count <= 3:
            expected_confidence = PreferenceConfidence.LOW
        elif effective_count <= 6:
            expected_confidence = PreferenceConfidence.MEDIUM
        else:
            expected_confidence = PreferenceConfidence.HIGH

        if self.confidence != expected_confidence:
            raise ValueError("偏好可信度与样本量不一致")

        expected_cold_start = (
            expected_confidence == PreferenceConfidence.NONE)
        if self.coldStart != expected_cold_start:
            raise ValueError("coldStart 与 confidence 不一致")

        category_ids = [item.categoryId for item in self.preferredCategories]
        if len(category_ids) != len(set(category_ids)):
            raise ValueError("偏好分类不能重复")

        interaction_ids = [
            item.commodityId for item in self.representativeInteractions
        ]
        if len(interaction_ids) != len(set(interaction_ids)):
            raise ValueError("代表性交互商品不能重复")

        if len(self.recentCommodityIds) != len(set(self.recentCommodityIds)):
            raise ValueError("近期商品 ID 不能重复")

        return self
