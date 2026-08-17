package com.pmsjl.service;

import com.pmsjl.model.dto.ai.internal.PostRagSnapshotItem;
import com.pmsjl.model.entity.Post;

/** Post 进入 RAG 快照、实时校验和展示前共同使用的资格规则。 */
public interface AiPostRagService {

    PostRagSnapshotItem toSnapshotItem(Post post);

    boolean isEligible(Post post, String sourceVersion);
}
