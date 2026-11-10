package vn.edu.fpt.savora.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Please enter your email address")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Please enter your password")
    private String password;
}
