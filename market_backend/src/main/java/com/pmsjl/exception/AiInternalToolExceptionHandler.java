package com.pmsjl.exception;

import com.pmsjl.controller.AiInternalToolController;
import com.pmsjl.controller.AiInternalRagController;
import com.pmsjl.model.dto.ai.internal.AgentErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Keeps internal Agent tool failures outside the public Result wrapper. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
        AiInternalToolController.class,
        AiInternalRagController.class
})
@Slf4j
public class AiInternalToolExceptionHandler {

    @ExceptionHandler(AiInternalToolException.class)
    public ResponseEntity<AgentErrorResponse> handle(
            AiInternalToolException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(exception.getStatus())
                .body(errorResponse(
                        request,
                        exception.getAgentErrorKey(),
                        exception.getMessage(),
                        exception.isRetryable()
                ));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<AgentErrorResponse> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .badRequest()
                .body(errorResponse(
                        request,
                        "AI_JAVA_TOOL_ARGUMENTS_INVALID",
                        exception.getMessage(),
                        false
                ));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<AgentErrorResponse> handleRuntimeException(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        log.error("Internal AI tool failed", exception);
        return ResponseEntity
                .internalServerError()
                .body(errorResponse(
                        request,
                        "AI_JAVA_TOOL_UNAVAILABLE",
                        "Java 内部 AI 工具暂不可用",
                        true
                ));
    }

    private AgentErrorResponse errorResponse(
            HttpServletRequest request,
            String errorKey,
            String message,
            boolean retryable
    ) {
        AgentErrorResponse response = new AgentErrorResponse();
        response.setRequestId(request.getHeader("X-Request-Id"));
        response.setAgentErrorKey(errorKey);
        response.setMessage(message);
        response.setRetryable(retryable);
        return response;
    }
}
