from decimal import Decimal
from enum import Enum

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    field_validator,
    model_validator,
)


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
