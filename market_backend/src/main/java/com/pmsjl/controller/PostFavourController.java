package com.pmsjl.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.common.Result;
import com.pmsjl.model.dto.post.PostQueryRequest;
import com.pmsjl.model.dto.postfavour.PostFavourAddRequest;
import com.pmsjl.model.vo.PostVO;
import com.pmsjl.service.PostFavourService;
import com.pmsjl.utils.ResultUtils;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 帖子收藏 前端控制器
 * </p>
 *
 * @author pmsjl
 * @since 2026-07-06
 */
@RestController
@RequestMapping("/post_favour")
public class PostFavourController {
    @Autowired
    private PostFavourService postFavourService;

    @PostMapping("/")
    public Result<Integer> doPostFavour(@RequestBody PostFavourAddRequest postFavourAddRequest,
                                        HttpServletRequest request) {
        ThrowUtils.throwIf(postFavourAddRequest==null, ErrorCode.PARAMS_ERROR);
        Long postId = postFavourAddRequest.getPostId();
        ThrowUtils.throwIf(postId==null||postId<=0,ErrorCode.PARAMS_ERROR);
        int result=postFavourService.doPostFavour(postFavourAddRequest,request);
        return ResultUtils.success(result);
    }

    @PostMapping("/my/list/page")
    public Result<Page<PostVO>> listMyFavourPostByPage(@RequestBody PostQueryRequest postQueryRequest,
                                                           HttpServletRequest request) {
        ThrowUtils.throwIf(postQueryRequest==null,ErrorCode.PARAMS_ERROR);
        Page<PostVO>page=postFavourService.listMyFavourPostByPage(postQueryRequest,request);
        return ResultUtils.success(page);

    }

}
