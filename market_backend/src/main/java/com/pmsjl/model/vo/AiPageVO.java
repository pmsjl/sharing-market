package com.pmsjl.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Stable page envelope used by the AI public contract. */
@Data
public class AiPageVO<T> implements Serializable {
    /** 当前页码，从 1 开始。 */
    private long current;

    /** 每页记录数量。 */
    private long pageSize;

    /** 符合条件的记录总数。 */
    private long total;

    /** 当前页的数据记录。 */
    private List<T> records = new ArrayList<>();

    private static final long serialVersionUID = 1L;
}
