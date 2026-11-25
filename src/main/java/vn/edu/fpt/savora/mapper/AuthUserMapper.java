package vn.edu.fpt.savora.mapper;

import org.springframework.stereotype.Component;
import vn.edu.fpt.savora.dto.response.AuthUserResponse;
import vn.edu.fpt.savora.entity.User;

@Component
public class AuthUserMapper {

    public AuthUserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }
        String first = user.getFirstName() != null ? user.getFirstName().trim() : "";
        String last = user.getLastName() != null ? user.getLastName().trim() : "";
        String fullName = (first + " " + last).trim();
        if (fullName.isEmpty()) {
            fullName = user.getEmail();
        }

        return AuthUserResponse.builder()
                .userId(user.getUserId())
                .fullName(fullName)
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }
}
