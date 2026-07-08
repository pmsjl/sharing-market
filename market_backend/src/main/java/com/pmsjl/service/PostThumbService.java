package com.pmsjl.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pmsjl.model.dto.postthumb.PostThumbAddRequest;
import com.pmsjl.model.entity.PostThumb;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 帖子点赞服务
 */
public interface PostThumbService extends IService<PostThumb> {

    int doPostThumb(PostThumbAddRequest postThumbAddRequest, HttpServletRequest request);

    int innerDoPostThumb(Long userId, Long postId);
}
