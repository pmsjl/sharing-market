package com.pmsjl.model.dto.userCommodityFavorites;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserCommodityFavoritesAddRequest implements Serializable {

    private Long commodityId;

    private static final long serialVersionUID = 1L;
}
