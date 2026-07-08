package com.pmsjl.service;

import com.pmsjl.common.DeleteRequest;
import com.pmsjl.model.dto.comment.CommentAddRequest;
import com.pmsjl.model.entity.Comment;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pmsjl.model.vo.CommentVO;
import com.pmsjl.model.vo.MyCommentVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author pmsjl
 * @since 2026-07-07
 */
public interface CommentService extends IService<Comment> {

    long addComment(CommentAddRequest commentAddRequest, HttpServletRequest request);

    boolean deleteComment(DeleteRequest deleteRequest, HttpServletRequest request);

    List<MyCommentVO> listMyComments(HttpServletRequest request);

    List<CommentVO> getCommentsByPostId(long postId, HttpServletRequest request);
}
