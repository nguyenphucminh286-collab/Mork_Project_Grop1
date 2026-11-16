package vn.edu.fpt.savora.exception;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.edu.fpt.savora.common.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
@RestControllerAdvice(annotations=RestController.class)
public class GlobalExceptionHandler {
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleApp(AppException ex) {
        ErrorCode c=ex.getErrorCode();
        return ResponseEntity.status(c.getStatus()).body(ApiResponse.error(c.getCode(),c.getMessage()));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBad(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error("COMMON_400",ex.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String,String> errors=new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errors.putIfAbsent(e.getField(),e.getDefaultMessage()));
        ApiResponse<Void> body=ApiResponse.error("VALIDATION_001","Validation failed.");
        body.setErrors(errors);
        return ResponseEntity.badRequest().body(body);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("COMMON_500","Internal server error."));
    }
}
