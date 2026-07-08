package com.pmsjl.controller;

import com.pmsjl.common.DeleteRequest;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.common.Result;
import com.pmsjl.model.dto.comment.CommentAddRequest;
import com.pmsjl.model.entity.Comment;
import com.pmsjl.model.vo.CommentVO;
import com.pmsjl.model.vo.MyCommentVO;
import com.pmsjl.service.CommentService;
import com.pmsjl.utils.ResultUtils;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author pmsjl
 * @since 2026-07-07
 */
@RestController
@RequestMapping("/comment")
public class CommentController {
    @Autowired
    private CommentService commentService;

    /**
     * 创建帖子评论
     *
     * @param commentAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public Result<Long> addComment(@RequestBody CommentAddRequest commentAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(commentAddRequest==null, ErrorCode.PARAMS_ERROR);
        long id=commentService.addComment(commentAddRequest,request);
        return ResultUtils.success(id);
    }
    @PostMapping("/delete")
    public Result<Boolean> deleteComment(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest==null,ErrorCode.PARAMS_ERROR);
        boolean result=commentService.deleteComment(deleteRequest,request);
        return ResultUtils.success(result);
    }
    @PostMapping("/myComments")
    public Result<List<MyCommentVO>> listMyComments(HttpServletRequest request) {
        List<MyCommentVO>list=commentService.listMyComments(request);
        return ResultUtils.success(list);

    }

    @GetMapping("/get/questonComment")
    public Result<List<CommentVO>> getCommentByPostId(long postId, HttpServletRequest request) {

        return ResultUtils.success(commentService.getCommentsByPostId(postId, request));
    }
}
