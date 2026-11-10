package vn.edu.fpt.savora.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthUserResponse {
    private Integer userId;
    private String fullName;
    private String email;
    private String phone;
    private String avatarUrl;
    private Integer roleId;
    private String roleName;
    private String redirectUrl;
}
