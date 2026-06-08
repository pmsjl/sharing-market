package com.pmsjl.model.dto.userCommodityFavorites;

import com.pmsjl.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
public class UserCommodityFavoritesQueryRequest extends PageRequest implements Serializable {

    private Long id;

    private Long userId;

    private Long commodityId;

    private Integer status;

    private String remark;

    private static final long serialVersionUID = 1L;
}
