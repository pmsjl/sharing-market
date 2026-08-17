package com.pmsjl.model.dto.ai.internal;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Cursor page returned to the offline Python RAG index builder. */
@Data
public class PostRagSnapshotResponse implements Serializable {

    private List<PostRagSnapshotItem> items = new ArrayList<>();

    private Long nextAfterId;

    private boolean hasMore;

    private static final long serialVersionUID = 1L;
}
