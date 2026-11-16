package vn.edu.fpt.savora.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Please enter your first name")
    private String firstName;

    @NotBlank(message = "Please enter your last name")
    private String lastName;

    @NotBlank(message = "Please enter your email address")
    @Email(regexp = "(?i)[^@]+@gmail\\.com", message = "Enter a valid @gmail.com address")
    private String email;

    @NotBlank(message = "Please enter your phone number")
    @Pattern(regexp = "[0-9]{10,15}", message = "Phone number must contain 10 to 15 digits")
    private String phone;

    private String gender;

    @NotBlank(message = "Please enter a password")
    @Size(min = 6, message = "Password must be at least 6 characters long")
    private String password;

    @NotBlank(message = "Please confirm your password")
    private String confirmPassword;
}
