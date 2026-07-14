package com.pmsjl.service.Impl;

import cn.hutool.core.date.DateTime;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.model.dto.post.PostQueryRequest;
import com.pmsjl.model.dto.postfavour.PostFavourAddRequest;
import com.pmsjl.model.entity.Post;
import com.pmsjl.model.entity.PostFavour;
import com.pmsjl.mapper.PostFavourMapper;
import com.pmsjl.model.entity.User;
import com.pmsjl.model.vo.PostVO;
import com.pmsjl.model.vo.UserVO;
import com.pmsjl.service.PostFavourService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pmsjl.service.PostService;
import com.pmsjl.service.UserService;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.ObjectUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.pmsjl.constant.RedisConstant.*;

/**
 * <p>
 * 帖子收藏 服务实现类
 * </p>
 *
 * @author pmsjl
 * @since 2026-07-06
 */
@Service
public class PostFavourServiceImpl extends ServiceImpl<PostFavourMapper, PostFavour> implements PostFavourService {
    @Autowired
    PostService postService;
    @Autowired
    RedissonClient redissonClient;
    @Autowired
    UserService userService;

    @Override
    public int doPostFavour(PostFavourAddRequest postFavourAddRequest, HttpServletRequest request) {
        Long postId = postFavourAddRequest.getPostId();
        Post oldPost = postService.getById(postId);
        if (oldPost == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "帖子不存在，无法收藏");
        }
        User user = userService.getLoginUser(request);
        Long userId = user.getId();
        RLock lock = redissonClient.getLock(POST_FAVOUR_KEY + userId + ":" + postId);
        boolean locked = false;
        try {
            locked = lock.tryLock(3, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "操作频繁，请稍后重试");
            }

            PostFavourServiceImpl proxy = (PostFavourServiceImpl) AopContext.currentProxy();
            return proxy.innerDoPostFavour(userId, postId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "操作被中断");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public Page<PostVO> listMyFavourPostByPage(PostQueryRequest postQueryRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        postQueryRequest.setFavourUserId(loginUser.getId());
        int current = postQueryRequest.getCurrent();
        int pageSize = postQueryRequest.getPageSize();
        if (current <= 0) {
            current = 1;
        }
        if (pageSize <= 0 || pageSize > 100) {
            pageSize = 10;
        }
        Page<Post> postPage = new Page<>(current, pageSize);
        Page<Post> favourPostPage = baseMapper.selectMyFavourPostPage(postPage, postQueryRequest);
        //mybatisplus中的这里继承的serviceImpl继承的类CrudRepository中有个baseMapper，而mybatisplus
        //会自动将这里的PostFavourMapper赋给baseMapper，所以这里本质就是在调用postFavourMapper的方法
        //还有一点这里传入的postPage不是给sql语句用的，是因为我们注册了mybatisplus的分页插件
        //所以只需要传入对应的Page类在这个方法里，它会自动检测到并利用分页插件替换语句实现分页，不需要显式写出
        List<Post> records = favourPostPage.getRecords();
        Page<PostVO> page = new Page<>(favourPostPage.getCurrent(), favourPostPage.getSize(), favourPostPage.getTotal());
        if (records == null || records.isEmpty()) {
            page.setRecords(List.of());
            return page;
        }

        // 直接联表分页，避免先查收藏关系再过滤帖子时 records 和 total 对不上的问题。
        List<PostVO> postVOList = records.stream().map(PostVO::objToVo).toList();
        Set<Long> userIdSet = postVOList.stream()
                .map(PostVO::getUserId)
                .filter(ObjectUtils::isNotEmpty)
                .collect(Collectors.toSet());
        if (!userIdSet.isEmpty()) {
            Map<Long, User> userMap = userService.listByIds(userIdSet).stream()
                    .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));
            postVOList.forEach(postVO -> {
                User user = userMap.get(postVO.getUserId());
                if (user != null) {
                    UserVO userVO = new UserVO();
                    BeanUtils.copyProperties(user, userVO);
                    postVO.setUser(userVO);
                }
            });
        }
        postVOList.forEach(postVO -> {
            postVO.setHasFavour(true);
            postVO.setHasThumb(false);
        });
        page.setRecords(postVOList);
        return page;
    }

    @Transactional(rollbackFor = Exception.class)
    public int innerDoPostFavour(Long userId, Long postId){
        PostFavour postFavour=new PostFavour();
        postFavour.setUserId(userId);
        postFavour.setPostId(postId);
        LambdaQueryWrapper<PostFavour> wrapper = new LambdaQueryWrapper<PostFavour>(postFavour);
        PostFavour oldPostFavour = this.getOne(wrapper);

        if(oldPostFavour==null){
            postFavour.setUpdateTime(DateTime.now());
            postFavour.setCreateTime(DateTime.now());
            boolean result = this.save(postFavour);
            ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR);
            boolean updateResult = postService.update().
                    eq("id", postId).
                    ge("favourNum", 0).
                    setSql("favourNum=favourNum+1").update();
            ThrowUtils.throwIf(!updateResult,ErrorCode.OPERATION_ERROR);
            return 1;
        }else{
            boolean result = this.remove(wrapper);
            ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR);
            boolean updateResult = postService.update().
                    eq("id", postId).
                    gt("favourNum", 0).
                    setSql("favourNum=favourNum-1").update();
            ThrowUtils.throwIf(!updateResult,ErrorCode.OPERATION_ERROR);
            return -1;

        }
    }

}
