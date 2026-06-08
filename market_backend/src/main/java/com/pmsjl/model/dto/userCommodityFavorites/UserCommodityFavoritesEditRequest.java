package com.pmsjl.model.dto.userCommodityFavorites;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserCommodityFavoritesEditRequest implements Serializable {

    private Long id;

    private Integer status;

    private static final long serialVersionUID = 1L;
}
