package com.pmsjl.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.model.dto.post.PostQueryRequest;
import com.pmsjl.model.dto.postfavour.PostFavourAddRequest;
import com.pmsjl.model.entity.PostFavour;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pmsjl.model.vo.PostVO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * <p>
 * 帖子收藏 服务类
 * </p>
 *
 * @author pmsjl
 * @since 2026-07-06
 */
public interface PostFavourService extends IService<PostFavour> {

    int doPostFavour(PostFavourAddRequest postFavourAddRequest, HttpServletRequest request);


    Page<PostVO> listMyFavourPostByPage(PostQueryRequest postQueryRequest, HttpServletRequest request);
}
