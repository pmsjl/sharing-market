SEARCH_COMMODITIES_TOOL = {
    "type": "function",
    "function": {
        "name": "search_commodities",
        "description": "搜索平台中当前已上架且有库存的商品",
        "parameters": {
            "type": "object",
            "properties": {
                "keywords": {
                    "type": "array",
                    "items": {
                        "type": "string",
                        "minLength": 1,
                        "maxLength": 30,
                    },
                    "maxItems": 5,
                    "description": (
                        "商品名称或描述的独立候选搜索词，最多5项。"
                        "每项只填写一个核心词或一个完整商品短语，"
                        "不同元素之间按OR匹配；"
                        "不要把多个候选词用空格连接在同一个元素中。"
                    ),
                },
                "categoryIds": {
                    "type": "array",
                    "items": {
                        "type": "string",
                        "description": "商品分类ID",
                    },
                    "description": "限定的商品分类ID,最多10项",
                },
                "minPrice": {
                    "type": "number",
                    "minimum": 0,
                    "description": "最低价格",
                },
                "maxPrice": {
                    "type": "number",
                    "minimum": 0,
                    "description": "最高价格，不能低于最低价格",
                },
                "degrees": {
                    "type": "array",
                    "items": {
                        "type": "string",
                        "description":"商品成色，最多10项",
                    },
                    "description": "允许的商品成色",
                },
                "excludeCommodityIds": {
                    "type": "array",
                    "items": {
                        "type": "string",
                    },
                    "description": "本轮需要排除的商品ID，最多20项",
                },
                "sortBy": {
                    "type": "string",
                    "enum": [
                        "RELEVANCE",
                        "PRICE_ASC",
                        "PRICE_DESC",
                        "FAVOUR_DESC",
                    ],
                    "description": (
                        "可选的商品排序策略。"
                        "仅当用户明确要求便宜优先或最低价时使用 PRICE_ASC；"
                        "仅当用户明确要求价格从高到低时使用 PRICE_DESC；"
                        "仅当用户明确要求热门或收藏量高时使用 FAVOUR_DESC；"
                        "除此之外不要主动指定特殊排序，可以省略本字段，"
                        "系统会使用默认值 RELEVANCE。"
                        "RELEVANCE 在当前阶段表示默认综合排序，不表示用户明确提出了相关度排序要求。"
                      ),
                },
                "limit": {
                    "type": "integer",
                    "minimum": 1,
                    "maximum": 20,
                    "description": "最多返回多少项",
                },
            },
            "required":[],
            "additionalProperties": False
        },
    },
}
