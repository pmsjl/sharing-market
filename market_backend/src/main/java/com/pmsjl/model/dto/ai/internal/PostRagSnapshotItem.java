package com.pmsjl.model.dto.ai.internal;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** A public Post snapshot that is safe to embed in the offline RAG index. */
@Data
public class PostRagSnapshotItem implements Serializable {

    private Long id;

    private String title;

    private String content;

    private List<String> tags = new ArrayList<>();

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    /** Millisecond update timestamp used to reject stale indexed content. */
    private String sourceVersion;

    private static final long serialVersionUID = 1L;
}
