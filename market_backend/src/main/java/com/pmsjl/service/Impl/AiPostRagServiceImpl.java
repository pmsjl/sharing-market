package com.pmsjl.service.Impl;

import cn.hutool.json.JSONUtil;
import com.pmsjl.model.dto.ai.internal.PostRagSnapshotItem;
import com.pmsjl.model.entity.Post;
import com.pmsjl.service.AiPostRagService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class AiPostRagServiceImpl implements AiPostRagService {

    private static final int MAX_TITLE_LENGTH = 80;
    private static final int MAX_CONTENT_LENGTH = 8192;
    private static final Set<String> DISALLOWED_TOPICS = Set.of(
            "文玩", "古董", "收藏品", "成人用品", "违规交易"
    );

    @Override
    public PostRagSnapshotItem toSnapshotItem(Post post) {
        if (post == null
                || post.getId() == null
                || post.getId() <= 0
                || Integer.valueOf(1).equals(post.getIsDelete())
                || StringUtils.isBlank(post.getTitle())
                || StringUtils.isBlank(post.getContent())
                || StringUtils.isBlank(post.getTags())
                || post.getTitle().length() > MAX_TITLE_LENGTH
                || post.getContent().length() > MAX_CONTENT_LENGTH
                || post.getCreateTime() == null
                || post.getUpdateTime() == null
                || DISALLOWED_TOPICS.stream().anyMatch(post.getTitle()::contains)) {
            return null;
        }

        List<String> tags;
        try {
            tags = JSONUtil.toList(post.getTags(), String.class).stream()
                    .filter(StringUtils::isNotBlank)
                    .map(String::trim)
                    .distinct()
                    .toList();
        } catch (RuntimeException exception) {
            return null;
        }
        if (tags.isEmpty() || tags.stream().anyMatch(DISALLOWED_TOPICS::contains)) {
            return null;
        }

        PostRagSnapshotItem item = new PostRagSnapshotItem();
        item.setId(post.getId());
        item.setTitle(post.getTitle());
        item.setContent(post.getContent());
        item.setTags(tags);
        item.setCreateTime(post.getCreateTime());
        item.setUpdateTime(post.getUpdateTime());
        item.setSourceVersion(Long.toString(post.getUpdateTime().getTime()));
        return item;
    }

    @Override
    public boolean isEligible(Post post, String sourceVersion) {
        String currentSourceVersion = Long.toString(post.getUpdateTime().getTime());
        return post!=null&&currentSourceVersion.equals(sourceVersion);
    }
}
