package dev.twelvetimes.vector.api.error;

import dev.twelvetimes.vector.api.contract.ApiModels.ErrorResponse;
import dev.twelvetimes.vector.task.TaskReadiness;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 所有管理API错误使用同一外部结构，数据库细节只留在服务端。 */
@RestControllerAdvice
public final class ApiExceptionHandler {
    private final TaskReadiness readiness;

    public ApiExceptionHandler(TaskReadiness readiness) { this.readiness = readiness; }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> business(BusinessException exception) {
        return ResponseEntity.status(exception.status()).body(new ErrorResponse(
                exception.code(), exception.getMessage(), exception.fieldErrors()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> malformed(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse("MALFORMED_JSON", "JSON正文无法解析", List.of()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> database(DataAccessException exception) {
        return ResponseEntity.status(503).body(new ErrorResponse("DATABASE_UNAVAILABLE", "数据库暂不可用", List.of()));
    }

    @ExceptionHandler(TransactionException.class)
    public ResponseEntity<ErrorResponse> transactionUnknown(TransactionException exception) {
        readiness.block("管理写入事务结果无法确认");
        return ResponseEntity.status(503).body(new ErrorResponse("RESULT_UNKNOWN", "写入结果无法确认，请查询当前状态", List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception exception) {
        return ResponseEntity.internalServerError().body(new ErrorResponse("INTERNAL_ERROR", "服务内部错误", List.of()));
    }
}
