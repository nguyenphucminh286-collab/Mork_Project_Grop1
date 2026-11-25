# Savora — Migration Notes (Spring Boot 4.1.2, Java 21, Thymeleaf)

- Preserved core JPA entities, repositories, `AuthService`, REST `AuthController`, CSS/JS/images, and the MySQL schema.
- Renamed `model` -> `entity`, moved `PasswordUtils` to `security/util`, and moved `AuthInterceptor` to `security/interceptor`.
- Added Thymeleaf templates and fragments for Login, Register, Home, Contact, QR Ordering, Staff/Chef Portals, and Admin Dashboard.
- Added `AppException`, `ErrorCode`, and `GlobalExceptionHandler` for REST API error handling.
- Added `AuthUserMapper` for auth response mapping in `AuthService`.
- Uses `ApiResponse<T>` for REST endpoints and `Model` for MVC pages.
- Default local server runs at **http://localhost:8080**.
- Database password is read from the `DB_PASSWORD` environment variable.
- Google OAuth2 is configured in `application-oauth2.yml` and accepts `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`.

## Quick Start
1. Install JDK 21, Maven, and MySQL 8.
2. Import `database/savora_mysql_schema.sql` (back up existing data first as it runs `DROP DATABASE`).
3. Set `DB_PASSWORD` to match your MySQL password.
4. Run `mvn clean test`, then `mvn spring-boot:run`.
5. Open http://localhost:8080.

## Known Notes
- `AuthInterceptor` manages role authorization via `HttpSession`; consider migrating to `UserDetailsService` + Spring Security authorities before full production hardening.
- If using custom Google OAuth2 credentials, configure `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` (with `SPRING_PROFILES_ACTIVE=oauth2` if needed).
