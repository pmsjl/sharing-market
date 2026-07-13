package com.pmsjl.model.dto.ai;

import com.pmsjl.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * Pagination input for loading one conversation's messages.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiMessageQueryRequest extends PageRequest implements Serializable {

    private static final long serialVersionUID = 1L;
}
