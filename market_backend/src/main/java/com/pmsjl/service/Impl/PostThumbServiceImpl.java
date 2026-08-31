package com.pmsjl.service.Impl;

import cn.hutool.core.date.DateTime;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.mapper.PostThumbMapper;
import com.pmsjl.model.dto.postthumb.PostThumbAddRequest;
import com.pmsjl.model.entity.Post;
import com.pmsjl.model.entity.PostThumb;
import com.pmsjl.model.entity.User;
import com.pmsjl.service.PostService;
import com.pmsjl.service.PostThumbService;
import com.pmsjl.service.UserService;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

import static com.pmsjl.constant.RedisConstant.POST_THUMB_KEY;

/**
 * 帖子点赞服务实现
 */
@Service
public class PostThumbServiceImpl extends ServiceImpl<PostThumbMapper, PostThumb> implements PostThumbService {

    @Autowired
    private PostService postService;

    @Autowired
    private UserService userService;

    @Autowired
    private RedissonClient redissonClient;

    @Lazy
    @Autowired
    private PostThumbService postThumbService;

    @Override
    public int doPostThumb(PostThumbAddRequest postThumbAddRequest, HttpServletRequest request) {
        Long postId = postThumbAddRequest.getPostId();
        Post oldPost = postService.getById(postId);
        if (oldPost == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "帖子不存在，无法点赞");
        }
        User loginUser = userService.getLoginUser();
        Long userId = loginUser.getId();
        RLock lock = redissonClient.getLock(POST_THUMB_KEY + userId + ":" + postId);
        try {
            boolean locked = lock.tryLock(3, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "操作频繁，请稍后重试");
            }
            PostThumbServiceImpl proxy = (PostThumbServiceImpl) AopContext.currentProxy();
            return proxy.innerDoPostThumb(userId, postId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "操作被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int innerDoPostThumb(Long userId, Long postId) {
        PostThumb postThumb = new PostThumb();
        postThumb.setUserId(userId);
        postThumb.setPostId(postId);
        LambdaQueryWrapper<PostThumb> wrapper = new LambdaQueryWrapper<PostThumb>(postThumb);
        PostThumb oldPostThumb = this.getOne(wrapper);

        if (oldPostThumb == null) {
            postThumb.setCreateTime(DateTime.now());
            postThumb.setUpdateTime(DateTime.now());
            boolean saved = this.save(postThumb);
            ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR);
            boolean updated = postService.update()
                    .eq("id", postId)
                    .ge("thumbNum", 0)
                    .setSql("thumbNum = thumbNum + 1")
                    .update();
            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR);
            return 1;
        } else {
            boolean removed = this.remove(wrapper);
            ThrowUtils.throwIf(!removed, ErrorCode.OPERATION_ERROR);
            boolean updated = postService.update()
                    .eq("id", postId)
                    .gt("thumbNum", 0)
                    .setSql("thumbNum = thumbNum - 1")
                    .update();
            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR);
            return -1;
        }
    }
}
