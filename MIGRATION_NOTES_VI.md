# Savora — Chuyển đổi sang Spring Boot 4.1.2, Java 21, Thymeleaf

- Đã giữ lại Entity, Repository, AuthService, REST AuthController, CSS/JS/Images, SQL schema.
- Đổi `model` -> `entity`, đưa `PasswordUtils` vào `security/util`, `AuthInterceptor` vào `security/interceptor`.
- Thêm Thymeleaf templates và fragments; chuyển Login, Register, Home, Contact.
- Thêm `AppException`, `ErrorCode`, `GlobalExceptionHandler` riêng cho REST API.
- Thêm MapStruct (`AuthUserMapper`) trong AuthService.
- Dùng `ApiResponse<T>` cho REST và `Model` cho MVC.
- Cấu hình mặc định chạy **http://localhost:8080**.
- Database password được đọc từ biến môi trường `DB_PASSWORD`.
- Google/Facebook OAuth2 được tách vào profile `oauth2` và yêu cầu 4 biến môi trường Client ID/Secret.

## Khởi động
1. Cài JDK 21, Maven và MySQL 8.
2. Import `database/savora_mysql_schema.sql` (sao lưu trước: có lệnh DROP DATABASE).
3. Đặt biến môi trường `DB_PASSWORD` theo mật khẩu MySQL.
4. Chạy `mvn clean test`, sau đó `mvn spring-boot:run`.
5. Mở http://localhost:8080.

## Giới hạn đã biết
- Chưa xác nhận build trên máy người dùng; cần Maven resolve dependency từ Maven Central.
- `AuthInterceptor` vẫn quản lý role qua session để giữ khả năng tương thích; phải chuyển sang
  `UserDetailsService` + Spring Security authorities trước khi production.
- REST `POST /api/auth/**` cần CSRF token. MVC Form đã tự thêm CSRF token theo Thymeleaf.
- Các phần Admin/Staff/Order chưa có code nghiệp vụ trong repository gốc, không được tự nhận là hoàn thiện.
- Nếu cấu hình `oauth2` thì khai báo đủ `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`,
  `FACEBOOK_CLIENT_ID`, `FACEBOOK_CLIENT_SECRET` và `SPRING_PROFILES_ACTIVE=oauth2`.
