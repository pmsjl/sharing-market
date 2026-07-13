package com.pmsjl.model.dto.ai.internal;

import com.pmsjl.model.enums.AiPreferenceSignalEnum;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Privacy-limited preference signals exposed to the Python Agent. */
@Data
public class UserPreferenceToolResponse implements Serializable {
    /** 本次工具调用的全链路请求标识。 */
    private String requestId;

    /** 查询偏好信号的用户 ID。 */
    private Long userId;

    /** 根据用户行为聚合出的偏好分类。 */
    private List<PreferredCategory> preferredCategories = new ArrayList<>();

    /** 用户近期交互过的商品 ID，用于减少重复推荐。 */
    private List<Long> recentCommodityIds = new ArrayList<>();

    @Data
    public static class PreferredCategory implements Serializable {
        /** 偏好商品分类 ID。 */
        private Long categoryId;

        /** 偏好商品分类名称。 */
        private String categoryName;

        /** 分类偏好权重，数值越大表示偏好越明显。 */
        private Double weight;

        /** 形成该偏好的行为信号类型。 */
        private List<AiPreferenceSignalEnum> signals = new ArrayList<>();

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
