package com.pmsjl.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Java 使用实时数据库内容生成的相关帖子卡片。 */
@Data
public class AiRelatedPostVO implements Serializable {
    private Long postId;
    private String title;
    private String excerpt;
    private List<String> tags = new ArrayList<>();
    private String targetPath;
    private static final long serialVersionUID = 1L;
}
