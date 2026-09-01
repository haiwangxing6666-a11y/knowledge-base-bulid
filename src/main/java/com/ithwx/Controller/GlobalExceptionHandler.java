package com.ithwx.Controller;

import com.ithwx.Dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理 @Valid 参数校验失败。
     */
    @ExceptionHandler(
            MethodArgumentNotValidException.class
    )
    public ResponseEntity<ApiError> validation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message =
                exception.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(
                                error ->
                                        error.getField()
                                                + "："
                                                + error.getDefaultMessage()
                        )
                        .distinct()
                        .collect(
                                Collectors.joining("；")
                        );

        return response(
                HttpStatus.BAD_REQUEST,
                message,
                request
        );
    }

    /**
     * 处理参数错误和文件过大。
     */
    @ExceptionHandler({
            IllegalArgumentException.class,
            MaxUploadSizeExceededException.class
    })
    public ResponseEntity<ApiError> badRequest(
            Exception exception,
            HttpServletRequest request
    ) {
        String message =
                exception
                        instanceof
                        MaxUploadSizeExceededException
                        ? "文件超过 50MB 限制"
                        : exception.getMessage();

        return response(
                HttpStatus.BAD_REQUEST,
                message,
                request
        );
    }

    /**
     * 处理未预料到的服务器错误。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error(
                "请求处理失败: {}",
                request.getRequestURI(),
                exception
        );

        String chain =
                exceptionChain(exception)
                        .toLowerCase();

        if (
                chain.contains("401")
                        || chain.contains("invalid_api_key")
                        || chain.contains("incorrect api key")
        ) {
            return response(
                    HttpStatus.BAD_GATEWAY,
                    "模型服务认证失败，请检查 "
                            + "SILICONFLOW_API_KEY "
                            + "是否有效，并重启应用",
                    request
            );
        }

        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "服务器处理失败，请查看后台日志",
                request
        );
    }

    // ==================== private 辅助方法 ====================

    /**
     * 创建统一的错误响应。
     */
    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String message,
            HttpServletRequest request
    ) {
        ApiError error = new ApiError(
                Instant.now(),
                status.value(),
                message,
                request.getRequestURI()
        );

        return ResponseEntity
                .status(status)
                .body(error);
    }

    /**
     * 收集异常及其全部 cause 的信息。
     */
    private String exceptionChain(
            Throwable throwable
    ) {
        StringBuilder result =
                new StringBuilder();

        for (
                Throwable current = throwable;
                current != null;
                current = current.getCause()
        ) {
            if (current.getMessage() != null) {
                result.append(
                        current.getMessage()
                ).append(' ');
            }
        }

        return result.toString();
    }
}