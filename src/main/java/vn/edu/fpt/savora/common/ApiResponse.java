package vn.edu.fpt.savora.common;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.Map;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private String errorCode;
    private String message;
    private T data;
    private Map<String,String> errors;
    @Builder.Default
    private LocalDateTime timestamp=LocalDateTime.now();
    public static <T> ApiResponse<T> ok(String message,T data){
        return ApiResponse.<T>builder().success(true).message(message).data(data).build();
    }
    public static <T> ApiResponse<T> ok(T data){return ok("Success",data);}
    public static <T> ApiResponse<T> error(String message){
        return ApiResponse.<T>builder().success(false).message(message).build();
    }
    public static <T> ApiResponse<T> error(String code,String message){
        return ApiResponse.<T>builder().success(false).errorCode(code).message(message).build();
    }
}
