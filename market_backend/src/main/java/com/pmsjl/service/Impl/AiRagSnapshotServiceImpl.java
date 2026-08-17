package com.pmsjl.service.Impl;

import com.pmsjl.config.AiAgentProperties;
import com.pmsjl.exception.AiInternalToolException;
import com.pmsjl.model.dto.ai.internal.PostRagSnapshotItem;
import com.pmsjl.model.dto.ai.internal.PostRagSnapshotResponse;
import com.pmsjl.model.entity.Post;
import com.pmsjl.service.AiRagSnapshotService;
import com.pmsjl.service.AiPostRagService;
import com.pmsjl.service.PostService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiRagSnapshotServiceImpl implements AiRagSnapshotService {

    static final int MAX_PAGE_SIZE = 200;

    @Autowired
    AiAgentProperties aiAgentProperties;

    @Autowired
    PostService postService;

    @Autowired
    AiPostRagService aiPostRagService;

    @Override
    public PostRagSnapshotResponse listPostSnapshots(
            Long afterId,
            Integer limit,
            HttpServletRequest request
    ) {
        validateInternalToken(request);
        if (afterId == null || afterId < 0) {
            throw invalidArgument("afterId 必须大于等于 0");
        }
        if (limit == null || limit < 1 || limit > MAX_PAGE_SIZE) {
            throw invalidArgument("limit 必须在 1 到 200 之间");
        }

        List<Post> records = postService.listRagSnapshotCandidates(
                afterId,
                limit + 1
        );
        //limit+1是为了判断后面还有没有值

        boolean hasMore = records.size() > limit;
        List<Post> scannedRecords = hasMore
                ? records.subList(0, limit)
                : records;
        List<PostRagSnapshotItem> items = scannedRecords.stream()
                .map(aiPostRagService::toSnapshotItem)
                .filter(item -> item != null)
                .toList();

        long nextAfterId = scannedRecords.isEmpty()
                ? afterId
                : scannedRecords.get(scannedRecords.size() - 1).getId();

        PostRagSnapshotResponse response = new PostRagSnapshotResponse();
        response.setItems(items);
        response.setNextAfterId(nextAfterId);
        response.setHasMore(hasMore);
        return response;
    }

    private void validateInternalToken(HttpServletRequest request) {
        String configuredToken = aiAgentProperties.getInternalToken();
        String requestToken = request.getHeader("X-Internal-Token");
        if (StringUtils.isBlank(configuredToken)
                || !configuredToken.equals(requestToken)) {
            throw new AiInternalToolException(
                    HttpStatus.UNAUTHORIZED,
                    "AI_JAVA_TOOL_UNAUTHORIZED",
                    "内部 Token 校验失败",
                    false
            );
        }
    }

    private AiInternalToolException invalidArgument(String message) {
        return new AiInternalToolException(
                HttpStatus.BAD_REQUEST,
                "AI_JAVA_TOOL_ARGUMENTS_INVALID",
                message,
                false
        );
    }
}
