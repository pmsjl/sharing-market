package com.pmsjl.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.annotation.AuthCheck;
import com.pmsjl.common.DeleteRequest;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.common.Result;
import com.pmsjl.constant.UserConstant;
import com.pmsjl.model.dto.post.PostAddRequest;
import com.pmsjl.model.dto.post.PostEditRequest;
import com.pmsjl.model.dto.post.PostQueryRequest;
import com.pmsjl.model.dto.post.PostUpdateRequest;
import com.pmsjl.model.entity.Post;
import com.pmsjl.model.vo.PostVO;
import com.pmsjl.service.PostService;
import com.pmsjl.utils.ResultUtils;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 帖子 前端控制器
 * </p>
 *
 * @author pmsjl
 * @since 2026-06-10
 */
@RestController
@RequestMapping("/post")
public class PostController {

    @Autowired
    private PostService postService;

    @PostMapping("/add")
    public Result<Long> addPost(@RequestBody PostAddRequest postAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(postAddRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = postService.addPost(postAddRequest, request);
        return ResultUtils.success(id);
    }

    @PostMapping("/delete")
    public Result<Boolean> deletePost(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null, ErrorCode.PARAMS_ERROR);
        Boolean result = postService.deletePost(deleteRequest, request);
        return ResultUtils.success(result);
    }

    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public Result<Boolean> updatePost(@RequestBody PostUpdateRequest postUpdateRequest) {
        ThrowUtils.throwIf(postUpdateRequest == null, ErrorCode.PARAMS_ERROR);
        Boolean result = postService.updatePost(postUpdateRequest);
        return ResultUtils.success(result);
    }

    @GetMapping("/get/vo")
    public Result<PostVO> getPostVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        PostVO postVO = postService.getPostVOById(id, request);
        return ResultUtils.success(postVO);
    }

    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public Result<Page<Post>> listPostByPage(@RequestBody PostQueryRequest postQueryRequest) {
        ThrowUtils.throwIf(postQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Page<Post> postPage = postService.listPostByPage(postQueryRequest);
        return ResultUtils.success(postPage);
    }

    @PostMapping("/list/page/vo")
    public Result<Page<PostVO>> listPostVOByPage(@RequestBody PostQueryRequest postQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(postQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Page<PostVO> postVOPage = postService.listPostVOByPage(postQueryRequest, request);
        return ResultUtils.success(postVOPage);
    }

    @PostMapping("/my/list/page/vo")
    public Result<Page<PostVO>> listMyPostVOByPage(@RequestBody PostQueryRequest postQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(postQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Page<PostVO> postVOPage = postService.listMyPostVOByPage(postQueryRequest, request);
        return ResultUtils.success(postVOPage);
    }

    @PostMapping("/search/page/vo")
    public Result<Page<PostVO>> searchPostVOByPage(@RequestBody PostQueryRequest postQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(postQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Page<PostVO> postVOPage = postService.searchPostVOByPage(postQueryRequest, request);
        return ResultUtils.success(postVOPage);
    }

    @PostMapping("/edit")
    public Result<Boolean> editPost(@RequestBody PostEditRequest postEditRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(postEditRequest == null, ErrorCode.PARAMS_ERROR);
        Boolean result = postService.editPost(postEditRequest, request);
        return ResultUtils.success(result);
    }
}
