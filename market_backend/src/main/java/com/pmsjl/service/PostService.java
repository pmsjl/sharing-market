package com.pmsjl.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.common.DeleteRequest;
import com.pmsjl.model.dto.post.PostAddRequest;
import com.pmsjl.model.dto.post.PostEditRequest;
import com.pmsjl.model.dto.post.PostQueryRequest;
import com.pmsjl.model.dto.post.PostUpdateRequest;
import com.pmsjl.model.entity.Post;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pmsjl.model.vo.PostVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * <p>
 * 帖子 服务类
 * </p>
 *
 * @author pmsjl
 * @since 2026-06-10
 */
public interface PostService extends IService<Post> {

    Long addPost(PostAddRequest postAddRequest, HttpServletRequest request);

    Boolean deletePost(DeleteRequest deleteRequest, HttpServletRequest request);

    Boolean updatePost(PostUpdateRequest postUpdateRequest);

    Boolean editPost(PostEditRequest postEditRequest, HttpServletRequest request);

    void validPost(Post post, boolean add);

    PostVO getPostVOById(long id, HttpServletRequest request);

    Page<Post> listPostByPage(PostQueryRequest postQueryRequest);

    Page<PostVO> listPostVOByPage(PostQueryRequest postQueryRequest, HttpServletRequest request);

    Page<PostVO> listMyPostVOByPage(PostQueryRequest postQueryRequest, HttpServletRequest request);

    Page<PostVO> searchPostVOByPage(PostQueryRequest postQueryRequest, HttpServletRequest request);

    List<Post> listRagSnapshotCandidates(long afterId, int scanLimit);
}
