package cn.bugstack.ai.trigger.http;

import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.types.enums.ResponseCode;
import cn.bugstack.ai.types.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 统一 REST 异常契约，避免把堆栈和内部实现细节暴露给调用方。 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Response<Void>> validation(MethodArgumentNotValidException exception) {
        FieldError error = exception.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = error == null ? "请求参数不合法"
                : error.getField() + " " + error.getDefaultMessage();
        return failure(HttpStatus.BAD_REQUEST, ResponseCode.ILLEGAL_PARAMETER, message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Response<Void>> illegalArgument(IllegalArgumentException exception) {
        return failure(HttpStatus.BAD_REQUEST, ResponseCode.ILLEGAL_PARAMETER, exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Response<Void>> illegalState(IllegalStateException exception) {
        return failure(HttpStatus.CONFLICT, ResponseCode.ILLEGAL_PARAMETER, exception.getMessage());
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Response<Void>> business(BizException exception) {
        String message = exception.getInfo() == null || exception.getInfo().isBlank()
                ? exception.getCode() : exception.getInfo();
        return failure(HttpStatus.BAD_REQUEST, ResponseCode.ILLEGAL_PARAMETER, message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<Void>> unexpected(Exception exception) {
        log.error("未处理的接口异常", exception);
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, ResponseCode.UN_ERROR, "服务内部错误，请结合 traceId 排查");
    }

    private static ResponseEntity<Response<Void>> failure(HttpStatus status,
                                                           ResponseCode code,
                                                           String message) {
        return ResponseEntity.status(status)
                .body(Response.<Void>builder()
                        .code(code.getCode())
                        .info(message == null || message.isBlank() ? code.getInfo() : message)
                        .build());
    }
}
