from decimal import Decimal
from enum import Enum

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    model_validator,
)


class CommoditySort(str, Enum):
    RELEVANCE = "RELEVANCE"
    PRICE_ASC = "PRICE_ASC"
    PRICE_DESC = "PRICE_DESC"
    FAVOUR_DESC = "FAVOUR_DESC"


class CommoditySearchArguments(BaseModel):
    keyword: str | None = Field(default=None, max_length=100)
    categoryIds: list[int] = Field(default_factory=list, max_length=10)
    minPrice: float | None = Field(default=None, ge=0)
    maxPrice: float | None = Field(default=None, ge=0)
    degrees: list[str] = Field(default_factory=list, max_length=10)
    excludeCommodityIds: list[int] = Field(default_factory=list, max_length=20)
    sortBy: CommoditySort=CommoditySort.RELEVANCE
    limit: int = Field(default=10, ge=1, le=20)

    @model_validator(mode="after")
    def validate_price_range(self):
        if (self.minPrice is not None and self.maxPrice is not None
                and self.maxPrice < self.minPrice):
            raise ValueError("maxPrice不能小于minPrice")

        return self
    """
     这里的方法就是在类实例化后自动触发进行检测
    """


class AiCommodityItem(BaseModel):
    id: int
    commodityName: str
    commodityDescription: str
    commodityAvatar: str
    degree: str
    commodityTypeId: str
    commodityTypeName: str
    commodityInventory: int
    price: Decimal
    viewNum: int
    favourNum: int


class CommoditySearchToolResponse(BaseModel):
    requestId: str
    matchedCount: int
    items: list[AiCommodityItem] = Field(default_factory=list)
