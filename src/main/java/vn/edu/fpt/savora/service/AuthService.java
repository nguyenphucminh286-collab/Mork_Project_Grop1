package vn.edu.fpt.savora.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.savora.common.PasswordUtils;
import vn.edu.fpt.savora.dto.request.LoginRequest;
import vn.edu.fpt.savora.dto.request.RegisterRequest;
import vn.edu.fpt.savora.dto.response.AuthUserResponse;
import vn.edu.fpt.savora.enums.Gender;
import vn.edu.fpt.savora.enums.UserStatus;
import vn.edu.fpt.savora.model.Role;
import vn.edu.fpt.savora.model.User;
import vn.edu.fpt.savora.repository.RoleRepository;
import vn.edu.fpt.savora.repository.UserRepository;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Xử lý nghiệp vụ Đăng nhập bằng Email/Mật khẩu, Google OAuth2 và Facebook OAuth2
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public AuthUserResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));

        if (!PasswordUtils.checkPassword(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password.");
        }

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new IllegalArgumentException("Your account has been locked. Please contact the administrator.");
        }

        if (user.getStatus() == UserStatus.PENDING) {
            throw new IllegalArgumentException("Your account has not been activated yet.");
        }

        return buildAuthUserResponse(user);
    }

    /**
     * Register a new Customer account in MySQL
     */
    @Transactional
    public AuthUserResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Password confirmation does not match.");
        }

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("This email address is already registered.");
        }

        Role customerRole = roleRepository.findByRoleName("CUSTOMER")
                .orElseGet(() -> roleRepository.save(
                        Role.builder().roleName("CUSTOMER").description("Customer").build()
                ));

        Gender genderEnum = Gender.OTHER;
        if (request.getGender() != null && !request.getGender().isBlank()) {
            try {
                genderEnum = Gender.valueOf(request.getGender().trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                genderEnum = Gender.OTHER;
            }
        }

        User newUser = User.builder()
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .email(email)
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .passwordHash(PasswordUtils.hashPassword(request.getPassword()))
                .avatarUrl("https://res.cloudinary.com/dej5mxdrt/image/upload/v1780061324/OIP_dbbjuo.jpg")
                .gender(genderEnum)
                .status(UserStatus.ACTIVE)
                .roles(Set.of(customerRole))
                .build();

        User savedUser = userRepository.save(newUser);
        return buildAuthUserResponse(savedUser);
    }

    /**
     * Xử lý đăng nhập OAuth2 dùng chung cho cả Google và Facebook
     */
    @Transactional
    public AuthUserResponse loginWithOAuth2(OAuth2User oauth2User) {
        String email = oauth2User.getAttribute("email");
        String socialId = oauth2User.getAttribute("id"); // Facebook trả về "id", Google trả về "sub"
        if (socialId == null) {
            socialId = oauth2User.getAttribute("sub");
        }

        // Nếu tài khoản Facebook đăng ký bằng SĐT (không có email) thì tạo email định danh nội bộ
        if (email == null || email.isBlank()) {
            email = "fb_" + (socialId != null ? socialId : UUID.randomUUID().toString().substring(0, 8)) + "@facebook.savora.vn";
        }
        String normalizedEmail = email.trim().toLowerCase();

        // Lấy họ tên (Google dùng given_name/family_name, Facebook dùng first_name/last_name/name)
        String givenName = firstNonBlank(oauth2User.getAttribute("given_name"), oauth2User.getAttribute("first_name"));
        String familyName = firstNonBlank(oauth2User.getAttribute("family_name"), oauth2User.getAttribute("last_name"));
        String fullNameAttr = oauth2User.getAttribute("name");

        // Lấy ảnh đại diện (Google trả về String, Facebook trả về Map lồng nhau { data: { url: "..." } })
        String picture = extractPictureUrl(oauth2User.getAttribute("picture"));

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);

        if (user == null) {
            Role customerRole = roleRepository.findByRoleName("CUSTOMER")
                    .orElseGet(() -> roleRepository.save(
                            Role.builder().roleName("CUSTOMER").description("Customer").build()
                    ));

            String firstName = (familyName != null && !familyName.isBlank()) ? familyName : "Customer";
            String lastName = (givenName != null && !givenName.isBlank())
                    ? givenName
                    : (fullNameAttr != null ? fullNameAttr : "Savora");

            user = User.builder()
                    .firstName(firstName)
                    .lastName(lastName)
                    .email(normalizedEmail)
                    .passwordHash(PasswordUtils.hashPassword(UUID.randomUUID().toString()))
                    .avatarUrl(picture != null ? picture : "https://res.cloudinary.com/dej5mxdrt/image/upload/v1780061324/OIP_dbbjuo.jpg")
                    .gender(Gender.OTHER)
                    .status(UserStatus.ACTIVE)
                    .roles(Set.of(customerRole))
                    .build();

            user = userRepository.save(user);
        } else if (picture != null && !picture.isBlank()) {
            user.setAvatarUrl(picture);
            user = userRepository.save(user);
        }

        return buildAuthUserResponse(user);
    }

    @SuppressWarnings("unchecked")
    private String extractPictureUrl(Object pictureAttr) {
        if (pictureAttr instanceof String str && !str.isBlank()) {
            return str;
        }
        if (pictureAttr instanceof Map<?, ?> map) {
            Object dataObj = map.get("data");
            if (dataObj instanceof Map<?, ?> dataMap) {
                Object urlObj = dataMap.get("url");
                if (urlObj instanceof String urlStr && !urlStr.isBlank()) {
                    return urlStr;
                }
            }
        }
        return null;
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    private AuthUserResponse buildAuthUserResponse(User user) {
        Role primaryRole = user.getRoles().stream().findFirst()
                .orElseGet(() -> Role.builder().roleId(2).roleName("CUSTOMER").build());

        String first = user.getFirstName() != null ? user.getFirstName().replace("Khách hàng", "").trim() : "";
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
                .roleId(primaryRole.getRoleId())
                .roleName(primaryRole.getRoleName())
                .redirectUrl("/home")
                .build();
    }
}
