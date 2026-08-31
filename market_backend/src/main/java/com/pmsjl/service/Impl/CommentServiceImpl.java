package com.pmsjl.service.Impl;

import cn.hutool.core.date.DateTime;
import com.pmsjl.common.DeleteRequest;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.mapper.UserMapper;
import com.pmsjl.model.dto.comment.CommentAddRequest;
import com.pmsjl.model.entity.Comment;
import com.pmsjl.mapper.CommentMapper;
import com.pmsjl.model.entity.Post;
import com.pmsjl.model.entity.User;
import com.pmsjl.model.vo.CommentVO;
import com.pmsjl.model.vo.MyCommentVO;
import com.pmsjl.model.vo.UserVO;
import com.pmsjl.service.CommentService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pmsjl.service.PostService;
import com.pmsjl.service.UserService;
import com.pmsjl.utils.ThrowUtils;
import com.pmsjl.utils.WordUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author pmsjl
 * @since 2026-07-07
 */
@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {
    @Autowired
    UserService userService;
    @Autowired
    PostService postService;
    @Autowired
    private UserMapper userMapper;

    @Override
    public long addComment(CommentAddRequest commentAddRequest, HttpServletRequest request) {
        Comment comment = new Comment();
        BeanUtils.copyProperties(commentAddRequest, comment);
        this.validComment(comment, true);
        User loginUser = userService.getLoginUser();
        Long userId = loginUser.getId();
        comment.setUserId(userId);
        comment.setCreateTime(DateTime.now());
        comment.setUpdateTime(DateTime.now());

        Long parentId = comment.getParentId();
        if (parentId != null) {
            Comment parentComment = this.getById(parentId);
            Long ancestorId = parentComment.getAncestorId();
            if (ancestorId == null) {
                comment.setAncestorId(parentId);
            } else {
                comment.setAncestorId(ancestorId);
            }
        }
        boolean result = save(comment);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return comment.getId();
    }

    @Override
    public boolean deleteComment(DeleteRequest deleteRequest, HttpServletRequest request) {
        Long id = deleteRequest.getId();
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        Comment comment = getById(id);
        ThrowUtils.throwIf(comment == null, ErrorCode.NOT_FOUND_ERROR, "评论不存在");
        User loginUser = userService.getLoginUser();
        Long userId = loginUser.getId();
        if ((!comment.getUserId().equals(userId)) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "权限不足");
        }
        List<Comment> commentList = lambdaQuery().eq(Comment::getPostId, comment.getPostId()).list();
        Map<Long, List<Comment>> childrenMap = commentList.stream().
                filter(item -> item.getParentId() != null).
                collect(Collectors.groupingBy(Comment::getParentId));
        Set<Long> deleteIdSet = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(id);
        while (!stack.isEmpty()) {
            Long currentId = stack.pop();
            if (!deleteIdSet.add(currentId)) {
                continue;
            }
            List<Comment> children = childrenMap.get(currentId);
            if (children != null) {
                children.forEach(child -> stack.push(child.getId()));
            }
        }
        boolean result = removeByIds(deleteIdSet);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除失败");
        return result;
    }

    @Override
    public List<MyCommentVO> listMyComments(HttpServletRequest request) {
        User loginUser = userService.getLoginUser();
        Long userId = loginUser.getId();
        List<Comment> commentList = lambdaQuery().eq(Comment::getUserId, userId).list();
        if (commentList.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> postIdSet = commentList.stream().
                map(Comment::getPostId).
                filter(Objects::nonNull).
                collect(Collectors.toSet());
        //直接用list去生成map有问题，因为他可能出现一个帖子多个评论，导致map多个相同的key+value出现
        //所以先变成去重后的set

        Map<Long, String> postMap = postIdSet.isEmpty() ? Collections.emptyMap() : postService.lambdaQuery().
                select(Post::getId, Post::getTitle).
                in(Post::getId, postIdSet).
                list().
                stream().
                collect(Collectors.toMap(Post::getId, Post::getTitle));
        //去重后为什么不采取一个一个查然后获取title组成map的形式呢？
        //80个查一次，和一次查80个效率是天差地别的，后者效率更高，所以应当尽可能利用这个set一次性查完。
        List<MyCommentVO> list = commentList.stream().map(comment -> {
            MyCommentVO myCommentVO = new MyCommentVO();
            BeanUtils.copyProperties(comment, myCommentVO);
            myCommentVO.setPostTitle(postMap.get(myCommentVO.getPostId()));
            return myCommentVO;
        }).toList();
        return list;


    }

    @Override
    public List<CommentVO> getCommentsByPostId(long postId, HttpServletRequest request) {
        List<Comment> commentList = lambdaQuery().eq(Comment::getPostId, postId).list();
        Set<Long> userIdSet = commentList.stream().map(Comment::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userService.lambdaQuery().
                in(User::getId, userIdSet).
                list().
                stream().
                collect(Collectors.toMap(User::getId, user -> user));

        List<CommentVO> commentVOList = commentList.stream().map(CommentVO::objToVo).toList();

        Map<Long, CommentVO> commentVOMap = commentVOList.stream().
                collect(Collectors.toMap(CommentVO::getId, commentVO -> commentVO));
        Map<Long, List<CommentVO>> repliesList = commentVOList.stream().
                filter(commentVO -> commentVO.getParentId() != null).
                collect(Collectors.groupingBy(CommentVO::getParentId));
        commentVOList.stream().forEach(commentVO -> {
            Long userId = commentVO.getUserId();
            ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR);
            User user = userMap.get(userId);
            UserVO userVO = new UserVO();
            BeanUtils.copyProperties(user, userVO);
            commentVO.setUser(userVO);
            Long parentId = commentVO.getParentId();
            if (parentId != null && parentId > 0) {
                CommentVO repliedComment = commentVOMap.get(parentId);
                User repliedUser = userMap.get(repliedComment.getUserId());
                UserVO repliedUserVO=new UserVO();
                BeanUtils.copyProperties(repliedUser,repliedUserVO);
                commentVO.setRepliedUser(repliedUserVO);
            }
            commentVO.setReplies(repliesList.get(commentVO.getId()));
        });
        return commentVOList.stream().filter(commentVO -> commentVO.getAncestorId()==null).toList();

    }

    private void validComment(Comment comment, boolean add) {
        ThrowUtils.throwIf(comment == null, ErrorCode.PARAMS_ERROR);

        String content = comment.getContent();
        Long parentId = comment.getParentId();
        Long postId = comment.getPostId();

        ThrowUtils.throwIf(StringUtils.isBlank(content), ErrorCode.OPERATION_ERROR, "内容不能为空");
        if (WordUtils.containsForbiddenWords(content)) {
            throw new BusinessException(ErrorCode.WORD_FORBIDDEN_ERROR, "包含违禁词");
        }
        ThrowUtils.throwIf(content.length() > 1024, ErrorCode.PARAMS_ERROR, "评论过长");


        if (add) {
            ThrowUtils.throwIf(postId == null || postId <= 0, ErrorCode.OPERATION_ERROR, "评价帖子id异常");
            Post post = postService.getById(postId);
            ThrowUtils.throwIf(post == null, ErrorCode.OPERATION_ERROR, "帖子不存在");
            //校验父id
            if (parentId != null) {
                ThrowUtils.throwIf(parentId <= 0, ErrorCode.PARAMS_ERROR, "父评论 id 异常");
                Comment parentComment = this.getById(parentId);
                ThrowUtils.throwIf(parentComment == null, ErrorCode.NOT_FOUND_ERROR);
                ThrowUtils.throwIf(!parentComment.getPostId().equals(postId), ErrorCode.PARAMS_ERROR, "父评论不属于当前帖子");
            }
        }
    }

}
