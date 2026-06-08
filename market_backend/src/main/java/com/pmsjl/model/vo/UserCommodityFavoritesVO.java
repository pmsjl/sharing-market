package com.pmsjl.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.pmsjl.model.entity.UserCommodityFavorites;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class UserCommodityFavoritesVO implements Serializable {

    private Long id;

    private Long userId;

    private Long commodityId;

    private String commodityName;

    private String commodityDescription;

    private String commodityAvatar;

    private String degree;

    private Long commodityTypeId;

    private Long adminId;

    private Integer isListed;

    private Integer commodityInventory;

    private BigDecimal price;

    private Integer viewNum;

    private Integer favourNum;

    private Integer status;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    private static final long serialVersionUID = 1L;

    public static UserCommodityFavorites voToObj(UserCommodityFavoritesVO userCommodityFavoritesVO) {
        if (userCommodityFavoritesVO == null) {
            return null;
        }
        UserCommodityFavorites userCommodityFavorites = new UserCommodityFavorites();
        BeanUtils.copyProperties(userCommodityFavoritesVO, userCommodityFavorites);
        return userCommodityFavorites;
    }

    public static UserCommodityFavoritesVO objToVo(UserCommodityFavorites userCommodityFavorites) {
        if (userCommodityFavorites == null) {
            return null;
        }
        UserCommodityFavoritesVO userCommodityFavoritesVO = new UserCommodityFavoritesVO();
        BeanUtils.copyProperties(userCommodityFavorites, userCommodityFavoritesVO);
        return userCommodityFavoritesVO;
    }
}
