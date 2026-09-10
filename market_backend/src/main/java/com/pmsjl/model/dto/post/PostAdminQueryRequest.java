package com.pmsjl.model.dto.post;

import com.pmsjl.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

/** 管理员帖子筛选请求。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PostAdminQueryRequest extends PageRequest implements Serializable {

    /** 标题关键词。 */
    private String title;

    /** 内容关键词。 */
    private String content;

    /** 必须同时包含的标签。 */
    private List<String> tags;

    /** 至少包含一个的标签；与其他筛选条件同时生效。 */
    private List<String> orTags;

    /** 帖子作者 id，由管理员指定。 */
    private Long userId;

    private static final long serialVersionUID = 1L;
}
