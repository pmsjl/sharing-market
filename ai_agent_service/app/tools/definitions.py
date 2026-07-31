SEARCH_COMMODITIES_TOOL = {
    "type":
    "function",
    "name":
    "search_commodities",
    "description": ("搜索平台中当前已上架且有库存的商品。"
                    "用户要求查询、推荐或比较平台商品，或需要实时价格、成色、库存时调用；"
                    "只咨询通用选购、验货、面交或支付安全时不调用。"),
    "parameters": {
        "type": "object",
        "properties": {
            "keywords": {
                "type":
                "array",
                "items": {
                    "type": "string",
                    "minLength": 1,
                    "maxLength": 30,
                },
                "maxItems":
                5,
                "description": ("商品名称或描述的独立候选搜索词，最多5项。"
                                "优先填写用户表达过的宽泛核心品类、主题或型号；"
                                "不要用模型自行联想到的品牌、书名或具体商品替代用户的宽泛词。"
                                "每项只填写一个核心词或一个完整商品短语，"
                                "不同元素之间按OR匹配；"
                                "不要把多个候选词用空格连接在同一个元素中。"
                                "若具体词无结果，后续调用应删除非必要修饰并退回最短核心词。"),
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
                    "description": "商品成色，最多10项",
                },
                "description": "允许的商品成色",
            },
            "excludeCommodityIds": {
                "type": "array",
                "items": {
                    "type": "string",
                },
                "description": ("本轮需要排除的商品ID，最多20项。"
                                "只能填写上下文或工具结果中明确出现的ID，不得猜测。"),
            },
            "sortBy": {
                "type":
                "string",
                "enum": [
                    "RELEVANCE",
                    "PRICE_ASC",
                    "PRICE_DESC",
                    "FAVOUR_DESC",
                ],
                "description": ("可选的商品排序策略。"
                                "仅当用户明确要求便宜优先或最低价时使用 PRICE_ASC；"
                                "仅当用户明确要求价格从高到低时使用 PRICE_DESC；"
                                "仅当用户明确要求热门或收藏量高时使用 FAVOUR_DESC；"
                                "除此之外不要主动指定特殊排序，可以省略本字段，"
                                "系统会使用默认值 RELEVANCE。"
                                "RELEVANCE 在当前阶段表示默认综合排序，不表示用户明确提出了相关度排序要求。"),
            },
            "limit": {
                "type": "integer",
                "minimum": 1,
                "maximum": 40,
                "description": (
                    "本次希望返回的候选数量，必填，范围1到40。"
                    "根据需求广度和比较需要自行选择："
                    "目标明确或条件较窄时选择较小值，"
                    "宽泛推荐、比较或再找找时选择较大值；"
                    "不得省略或机械固定为同一数值。"
                ),
            },
        },
        "required": ["limit"],
        "additionalProperties": False,
    },
    # OpenAI strict=true 同样要求 properties 中的全部字段都列入 required；
    # 语义上的可选字段需要改成 nullable 后仍列入 required。
    # 商品搜索需要允许模型只传部分筛选条件，所以这里明确使用 strict=false，
    # 再由 CommoditySearchArguments 做业务参数校验。
    "strict":
    False,
}

GET_MY_PREFERENCE_SIGNALS_TOOL = {
    "type":
    "function",
    "name":
    "get_my_preference_signals",
    "description": ("获取当前用户由有效收藏和已支付购买生成的脱敏长期偏好画像，"
                    "包括代表性历史交互、分类、成交单价、收藏商品当前价格、"
                    "成色、近期历史交互商品ID及可信度。"
                    "用户明确要求个性化推荐，或需求宽泛且缺少筛选依据时调用；"
                    "未要求个性化且条件已经明确，或只咨询通用交易安全时不调用。"
                    "历史交互商品不代表当前在售，推荐前必须重新搜索。"),
    "parameters": {
        "type": "object",
        "properties": {},
        "required": [],
        "additionalProperties": False,
    },
    "strict":
    True,
}
