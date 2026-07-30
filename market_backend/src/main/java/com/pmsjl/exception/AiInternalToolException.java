package com.pmsjl.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Exception whose HTTP status is part of the Java/Python internal contract. */
@Getter
public class AiInternalToolException extends RuntimeException {

    private final HttpStatus status;
    private final String agentErrorKey;
    private final boolean retryable;

    public AiInternalToolException(
            HttpStatus status,
            String agentErrorKey,
            String message,
            boolean retryable
    ) {
        super(message);
        this.status = status;
        this.agentErrorKey = agentErrorKey;
        this.retryable = retryable;
    }
}
