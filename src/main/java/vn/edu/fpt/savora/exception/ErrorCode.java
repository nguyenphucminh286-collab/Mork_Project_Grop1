package vn.edu.fpt.savora.exception;
import lombok.Getter;
import org.springframework.http.HttpStatus;
@Getter
public enum ErrorCode {
    INVALID_CREDENTIALS("AUTH_001", "Invalid email or password.", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED("AUTH_002", "Your account has been locked.", HttpStatus.FORBIDDEN),
    ACCOUNT_PENDING("AUTH_003", "Your account is not active.", HttpStatus.FORBIDDEN),
    EMAIL_ALREADY_EXISTS("AUTH_004", "This email is already registered.", HttpStatus.CONFLICT),
    PASSWORD_MISMATCH("AUTH_005", "Password confirmation does not match.", HttpStatus.BAD_REQUEST),
    VALIDATION_ERROR("VALIDATION_001", "Invalid input data.", HttpStatus.BAD_REQUEST),
    BAD_REQUEST("COMMON_400", "Bad request.", HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR("COMMON_500", "Internal server error.", HttpStatus.INTERNAL_SERVER_ERROR);
    private final String code;
    private final String message;
    private final HttpStatus status;
    ErrorCode(String code,String message,HttpStatus status) {
        this.code=code;this.message=message;this.status=status;
    }
}
