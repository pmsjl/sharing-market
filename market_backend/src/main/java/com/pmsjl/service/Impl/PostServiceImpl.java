package com.pmsjl.service.Impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.common.DeleteRequest;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.model.dto.post.PostAddRequest;
import com.pmsjl.model.dto.post.PostEditRequest;
import com.pmsjl.model.dto.post.PostQueryRequest;
import com.pmsjl.model.dto.post.PostUpdateRequest;
import com.pmsjl.model.entity.Post;
import com.pmsjl.mapper.PostMapper;
import com.pmsjl.model.entity.User;
import com.pmsjl.model.vo.PostVO;
import com.pmsjl.model.vo.UserVO;
import com.pmsjl.service.PostService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pmsjl.service.UserService;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 帖子 服务实现类
 * </p>
 *
 * @author pmsjl
 * @since 2026-06-10
 */
@Service
public class PostServiceImpl extends ServiceImpl<PostMapper, Post> implements PostService {

    private static final Set<String> ALLOWED_POST_SORT_FIELDS = Set.of("id", "createTime", "updateTime", "thumbNum", "favourNum");

    @Autowired
    private UserService userService;

    @Override
    public Long addPost(PostAddRequest postAddRequest, HttpServletRequest request) {
        Post post = new Post();
        BeanUtils.copyProperties(postAddRequest, post);
        post.setTags(JSONUtil.toJsonStr(postAddRequest.getTags() == null ? List.of() : postAddRequest.getTags()));
        User loginUser = userService.getLoginUser(request);
        post.setUserId(loginUser.getId());
        post.setThumbNum(0);
        post.setFavourNum(0);
        validPost(post, true);
        boolean result = save(post);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return post.getId();
    }

    @Override
    public Boolean deletePost(DeleteRequest deleteRequest, HttpServletRequest request) {
        Long id = deleteRequest.getId();
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        Post oldPost = getById(id);
        ThrowUtils.throwIf(oldPost == null, ErrorCode.NOT_FOUND_ERROR);
        checkOwnerOrAdmin(oldPost, request);
        boolean result = removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updatePost(PostUpdateRequest postUpdateRequest) {
        Long id = postUpdateRequest.getId();
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        Post oldPost = getById(id);
        ThrowUtils.throwIf(oldPost == null, ErrorCode.NOT_FOUND_ERROR);
        Post post = new Post();
        BeanUtils.copyProperties(postUpdateRequest, post);
        if (postUpdateRequest.getTags() != null) {
            post.setTags(JSONUtil.toJsonStr(postUpdateRequest.getTags()));
        }
        post.setUserId(null);
        post.setThumbNum(null);
        post.setFavourNum(null);
        validPost(post, false);
        boolean result = updateById(post);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean editPost(PostEditRequest postEditRequest, HttpServletRequest request) {
        Long id = postEditRequest.getId();
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        Post oldPost = getById(id);
        ThrowUtils.throwIf(oldPost == null, ErrorCode.NOT_FOUND_ERROR);
        checkOwnerOrAdmin(oldPost, request);
        Post post = new Post();
        BeanUtils.copyProperties(postEditRequest, post);
        if (postEditRequest.getTags() != null) {
            post.setTags(JSONUtil.toJsonStr(postEditRequest.getTags()));
        }
        post.setUserId(null);
        post.setThumbNum(null);
        post.setFavourNum(null);
        validPost(post, false);
        boolean result = updateById(post);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return true;
    }

    @Override
    public void validPost(Post post, boolean add) {
        ThrowUtils.throwIf(post == null, ErrorCode.PARAMS_ERROR);
        String title = post.getTitle();
        String content = post.getContent();
        String tags = post.getTags();
        Long userId = post.getUserId();
        if (add) {
            ThrowUtils.throwIf(StringUtils.isAnyBlank(title, content, tags), ErrorCode.PARAMS_ERROR);
            ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR);
        }
        if (title != null) {
            ThrowUtils.throwIf(StringUtils.isBlank(title), ErrorCode.PARAMS_ERROR, "标题不能为空");
            ThrowUtils.throwIf(title.length() > 80, ErrorCode.PARAMS_ERROR, "标题过长");
        }
        if (content != null) {
            ThrowUtils.throwIf(StringUtils.isBlank(content), ErrorCode.PARAMS_ERROR, "内容不能为空");
            ThrowUtils.throwIf(content.length() > 8192, ErrorCode.PARAMS_ERROR, "内容过长");
        }
    }

    @Override
    public PostVO getPostVOById(long id, HttpServletRequest request) {
        Post post = getById(id);
        ThrowUtils.throwIf(post == null, ErrorCode.NOT_FOUND_ERROR);
        return getPostVO(post, request);
    }

    @Override
    public Page<Post> listPostByPage(PostQueryRequest postQueryRequest) {
        int current = normalizeCurrent(postQueryRequest.getCurrent());
        int pageSize = normalizePageSize(postQueryRequest.getPageSize(), 100);
        Page<Post> page = new Page<>(current, pageSize);
        applyPostOrder(page, postQueryRequest);
        return page(page, getQueryWrapper(postQueryRequest));
    }

    @Override
    public Page<PostVO> listPostVOByPage(PostQueryRequest postQueryRequest, HttpServletRequest request) {
        limitPublicPageSize(postQueryRequest);
        Page<Post> postPage = listPostByPage(postQueryRequest);
        return getPostVOPage(postPage, request);
    }

    @Override
    public Page<PostVO> listMyPostVOByPage(PostQueryRequest postQueryRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        postQueryRequest.setUserId(loginUser.getId());
        return listPostVOByPage(postQueryRequest, request);
    }

    @Override
    public Page<PostVO> searchPostVOByPage(PostQueryRequest postQueryRequest, HttpServletRequest request) {
        return listPostVOByPage(postQueryRequest, request);
    }

    private QueryWrapper<Post> getQueryWrapper(PostQueryRequest postQueryRequest) {
        QueryWrapper<Post> queryWrapper = new QueryWrapper<>();
        if (postQueryRequest == null) {
            return queryWrapper;
        }
        String searchText = postQueryRequest.getSearchText();
        Long id = postQueryRequest.getId();
        Long notId = postQueryRequest.getNotId();
        String title = postQueryRequest.getTitle();
        String content = postQueryRequest.getContent();
        List<String> tags = postQueryRequest.getTags();
        List<String> orTags = postQueryRequest.getOrTags();
        Long userId = postQueryRequest.getUserId();

        if (StringUtils.isNotBlank(searchText)) {
            queryWrapper.and(wrapper -> wrapper.like("title", searchText).or().like("content", searchText));
        }
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.ne(ObjectUtils.isNotEmpty(notId), "id", notId);
        queryWrapper.like(StringUtils.isNotBlank(title), "title", title);
        queryWrapper.like(StringUtils.isNotBlank(content), "content", content);
        if (tags != null && !tags.isEmpty()) {
            tags.stream()
                    .filter(StringUtils::isNotBlank)
                    .forEach(tag -> queryWrapper.like("tags", "\"" + tag + "\""));
        }
        if (orTags != null && !orTags.isEmpty()) {
            List<String> validOrTags = orTags.stream().filter(StringUtils::isNotBlank).toList();
            if (!validOrTags.isEmpty()) {
                queryWrapper.and(wrapper -> {
                    for (int i = 0; i < validOrTags.size(); i++) {
                        if (i == 0) {
                            wrapper.like("tags", "\"" + validOrTags.get(i) + "\"");
                        } else {
                            wrapper.or().like("tags", "\"" + validOrTags.get(i) + "\"");
                        }
                    }
                });
            }
        }
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        return queryWrapper;
    }

    private PostVO getPostVO(Post post, HttpServletRequest request) {
        PostVO postVO = PostVO.objToVo(post);
        User user = userService.getById(post.getUserId());
        if (user != null) {
            UserVO userVO = new UserVO();
            BeanUtils.copyProperties(user, userVO);
            postVO.setUser(userVO);
        }
        postVO.setHasThumb(false);
        postVO.setHasFavour(false);
        return postVO;
    }

    private Page<PostVO> getPostVOPage(Page<Post> postPage, HttpServletRequest request) {
        List<Post> records = postPage.getRecords();
        Page<PostVO> page = new Page<>(postPage.getCurrent(), postPage.getSize(), postPage.getTotal());
        if (records == null || records.isEmpty()) {
            page.setRecords(List.of());
            return page;
        }
        List<PostVO> postVOList = records.stream().map(PostVO::objToVo).toList();
        Set<Long> userIds = records.stream()
                .map(Post::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!userIds.isEmpty()) {
            Map<Long, User> userMap = userService.listByIds(userIds).stream()
                    .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));
            postVOList.forEach(postVO -> {
                User user = userMap.get(postVO.getUserId());
                if (user != null) {
                    UserVO userVO = new UserVO();
                    BeanUtils.copyProperties(user, userVO);
                    postVO.setUser(userVO);
                }
                postVO.setHasThumb(false);
                postVO.setHasFavour(false);
            });
        } else {
            postVOList.forEach(postVO -> {
                postVO.setHasThumb(false);
                postVO.setHasFavour(false);
            });
        }
        page.setRecords(postVOList);
        return page;
    }

    private void checkOwnerOrAdmin(Post post, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if (!Objects.equals(loginUser.getId(), post.getUserId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }

    private int normalizeCurrent(int current) {
        return current <= 0 ? 1 : current;
    }

    private int normalizePageSize(int pageSize, int maxPageSize) {
        if (pageSize <= 0) {
            return 10;
        }
        return Math.min(pageSize, maxPageSize);
    }

    private void limitPublicPageSize(PostQueryRequest postQueryRequest) {
        ThrowUtils.throwIf(postQueryRequest.getPageSize() > 20, ErrorCode.PARAMS_ERROR);
    }

    private void applyPostOrder(Page<Post> page, PostQueryRequest postQueryRequest) {
        String sortField = postQueryRequest.getSortField();
        String sortOrder = postQueryRequest.getSortOrder();
        if (StringUtils.isNotBlank(sortField) && ALLOWED_POST_SORT_FIELDS.contains(sortField)) {
            if ("asc".equalsIgnoreCase(sortOrder)) {
                page.addOrder(OrderItem.asc(sortField));
            } else {
                page.addOrder(OrderItem.desc(sortField));
            }
        } else {
            page.addOrder(OrderItem.desc("createTime"));
        }
    }
}
