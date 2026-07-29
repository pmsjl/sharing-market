package com.pmsjl.model.vo;

import com.pmsjl.model.enums.AiIntentEnum;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Typed structured content embedded in an assistant message. */
@Data
public class AiStructuredContentVO implements Serializable {
    /** Agent 判断出的本轮用户意图。 */
    private AiIntentEnum intent;

    /** 对本轮结论的简短结构化摘要。 */
    private String summary;

    /** 已由 Java 查询和校验的推荐商品列表。 */
    private List<AiRecommendationVO> recommendations = new ArrayList<>();

    /** 面向用户的购买检查或决策建议。 */
    private List<String> purchaseAdvice = new ArrayList<>();

    /** 需要用户重点注意的交易风险。 */
    private List<String> warnings = new ArrayList<>();

    /** 可用于继续检索平台商品的关键词。 */
    private List<String> searchKeywords = new ArrayList<>();

    /** 已由 Java 校验并生成安全跳转地址的 RAG 来源。 */
    private List<AiRagSourceVO> sources = new ArrayList<>();

    private static final long serialVersionUID = 1L;
}
