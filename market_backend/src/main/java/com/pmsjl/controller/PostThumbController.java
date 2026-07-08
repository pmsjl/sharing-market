package com.pmsjl.controller;

import com.pmsjl.common.ErrorCode;
import com.pmsjl.common.Result;
import com.pmsjl.model.dto.postthumb.PostThumbAddRequest;
import com.pmsjl.service.PostThumbService;
import com.pmsjl.utils.ResultUtils;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 帖子点赞接口
 */
@RestController
@RequestMapping("/post_thumb")
public class PostThumbController {

    @Autowired
    private PostThumbService postThumbService;

    @PostMapping("/")
    public Result<Integer> doThumb(@RequestBody PostThumbAddRequest postThumbAddRequest,
                                   HttpServletRequest request) {
        ThrowUtils.throwIf(postThumbAddRequest == null, ErrorCode.PARAMS_ERROR);
        Long postId = postThumbAddRequest.getPostId();
        ThrowUtils.throwIf(postId == null || postId <= 0, ErrorCode.PARAMS_ERROR);
        int result = postThumbService.doPostThumb(postThumbAddRequest, request);
        return ResultUtils.success(result);
    }
}
