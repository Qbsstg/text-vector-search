package dev.twelvetimes.vector.api.error;

import dev.twelvetimes.vector.api.contract.ApiModels.FieldError;
import java.util.List;

/** 携带可公开的错误信息，不把SQL、堆栈或数据库异常消息带到HTTP边界。 */
public final class BusinessException extends RuntimeException {
    private final int status;
    private final String code;
    private final List<FieldError> fieldErrors;

    public BusinessException(int status, String code, String message) {
        this(status, code, message, List.of());
    }

    public BusinessException(int status, String code, String message, List<FieldError> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public int status() { return status; }
    public String code() { return code; }
    public List<FieldError> fieldErrors() { return fieldErrors; }

    public static BusinessException invalid(String field, String message) {
        return new BusinessException(400, "VALIDATION_ERROR", message, List.of(new FieldError(field, message)));
    }
}
