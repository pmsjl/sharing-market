package com.pmsjl.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.model.dto.post.PostQueryRequest;
import com.pmsjl.model.entity.Post;
import com.pmsjl.model.entity.PostFavour;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 帖子收藏 Mapper 接口
 * </p>
 *
 * @author pmsjl
 * @since 2026-07-06
 */
public interface PostFavourMapper extends BaseMapper<PostFavour> {

    Page<Post> selectMyFavourPostPage(Page<Post> page, @Param("queryRequest") PostQueryRequest queryRequest);
}
