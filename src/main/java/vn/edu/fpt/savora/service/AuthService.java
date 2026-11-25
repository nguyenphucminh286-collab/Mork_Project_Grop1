package vn.edu.fpt.savora.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.savora.security.util.PasswordUtils;
import vn.edu.fpt.savora.dto.request.LoginRequest;
import vn.edu.fpt.savora.dto.request.RegisterRequest;
import vn.edu.fpt.savora.dto.response.AuthUserResponse;
import vn.edu.fpt.savora.enums.Gender;
import vn.edu.fpt.savora.enums.UserStatus;
import vn.edu.fpt.savora.entity.Role;
import vn.edu.fpt.savora.entity.User;
import vn.edu.fpt.savora.repository.RoleRepository;
import vn.edu.fpt.savora.mapper.AuthUserMapper;
import vn.edu.fpt.savora.exception.AppException;
import vn.edu.fpt.savora.exception.ErrorCode;
import vn.edu.fpt.savora.repository.UserRepository;

import java.util.Set;
import java.util.UUID;

/**
 * Handles authentication business logic for Email/Password and Google OAuth2 sign-in.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthUserMapper authUserMapper;

    public AuthUserResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));

        if (!PasswordUtils.checkPassword(request.getPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        }

        if (user.getStatus() == UserStatus.PENDING) {
            throw new AppException(ErrorCode.ACCOUNT_PENDING);
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
            throw new AppException(ErrorCode.PASSWORD_MISMATCH);
        }

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
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
     * Handles Google OAuth2 sign-in and user provisioning.
     */
    @Transactional
    public AuthUserResponse loginWithOAuth2(OAuth2User oauth2User) {
        String email = oauth2User.getAttribute("email");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Google account did not provide an email address.");
        }
        String normalizedEmail = email.trim().toLowerCase();

        String givenName = oauth2User.getAttribute("given_name");
        String familyName = oauth2User.getAttribute("family_name");
        String fullNameAttr = oauth2User.getAttribute("name");
        Object pictureAttr = oauth2User.getAttribute("picture");
        String picture = (pictureAttr instanceof String str && !str.isBlank()) ? str : null;

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
        } else {
            if (user.getStatus() == UserStatus.LOCKED) {
                throw new AppException(ErrorCode.ACCOUNT_LOCKED);
            }
            if (user.getStatus() == UserStatus.PENDING) {
                throw new AppException(ErrorCode.ACCOUNT_PENDING);
            }
            if (picture != null && !picture.isBlank()) {
                user.setAvatarUrl(picture);
                user = userRepository.save(user);
            }
        }

        return buildAuthUserResponse(user);
    }

    private AuthUserResponse buildAuthUserResponse(User user) {
        Role primaryRole = user.getRoles().stream().findFirst()
                .orElseGet(() -> Role.builder().roleId(2).roleName("CUSTOMER").build());

        AuthUserResponse result = authUserMapper.toResponse(user);
        result.setRoleId(primaryRole.getRoleId());
        result.setRoleName(primaryRole.getRoleName());
        String redirect = "/home";
        if ("ADMIN".equalsIgnoreCase(primaryRole.getRoleName()) || Integer.valueOf(1).equals(primaryRole.getRoleId())) {
            redirect = "/admin/dashboard";
        } else if ("STAFF".equalsIgnoreCase(primaryRole.getRoleName()) || Integer.valueOf(3).equals(primaryRole.getRoleId())) {
            redirect = "/staff/tables";
        } else if ("CHEF".equalsIgnoreCase(primaryRole.getRoleName()) || Integer.valueOf(4).equals(primaryRole.getRoleId())) {
            redirect = "/staff/kitchen";
        }
        result.setRedirectUrl(redirect);
        return result;
    }
}
