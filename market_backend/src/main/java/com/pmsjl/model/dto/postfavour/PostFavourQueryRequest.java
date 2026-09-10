package com.pmsjl.model.dto.postfavour;

import com.pmsjl.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

/** 我的收藏查询请求，收藏者由服务端根据登录状态确定。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PostFavourQueryRequest extends PageRequest implements Serializable {

    /** 标题关键词。 */
    private String title;

    /** 必须同时包含的标签；不传或为空时不限制标签。 */
    private List<String> tags;

    /** 至少包含一个的标签；与其他筛选条件同时生效。 */
    private List<String> orTags;

    private static final long serialVersionUID = 1L;
}
