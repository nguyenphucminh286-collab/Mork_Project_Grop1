# Savora - Hệ thống Quản lý Nhà hàng (Restaurant Management System)

Dự án **Savora** được khởi tạo với khung kiến trúc chuẩn **Java 17 + Spring Boot 3.2.5 + MySQL + HTML/CSS/JS** chạy trên **VS Code**.

## 1. Các phần đã được thiết lập ở Commit đầu tiên (Khung dự án & Login)
1. **Môi trường & Cấu hình**:
   - [`pom.xml`](pom.xml): Java 17, Spring Boot Web, Spring Data JPA, Spring Security OAuth2 Client (Google Login), MySQL Driver, Lombok, jBCrypt.
   - [`application.properties`](src/main/resources/application.properties): Kết nối MySQL (`savora_db`), cấu hình Google OAuth2 Client ID & Secret, cổng `8083`.
   - [`database/savora_mysql_schema.sql`](database/savora_mysql_schema.sql): Script khởi tạo 14 bảng MySQL và dữ liệu mẫu.
   - [`.vscode/`](.vscode/launch.json): Cấu hình chạy & debug 1-click trên VS Code.
2. **Model & Enums dùng chung cho toàn nhóm**:
   - `vn.edu.fpt.savora.model.*`: Đầy đủ 13 JPA Entities (`User`, `Role`, `Category`, `Dish`, `DishImage`, `DiningTable`, `Reservation`, `RestaurantOrder`, `OrderItem`, `Payment`, `Voucher`, `Review`, `EmailVerification`).
   - `vn.edu.fpt.savora.enums.*`: Đầy đủ 11 Enums trạng thái.
3. **Chức năng Đăng nhập (Email/Password + Google OAuth2)**:
   - Backend: [`SecurityConfig.java`](src/main/java/vn/edu/fpt/savora/config/SecurityConfig.java), [`AuthController.java`](src/main/java/vn/edu/fpt/savora/controller/auth/AuthController.java), [`AuthService.java`](src/main/java/vn/edu/fpt/savora/service/AuthService.java), [`UserRepository.java`](src/main/java/vn/edu/fpt/savora/repository/UserRepository.java), [`RoleRepository.java`](src/main/java/vn/edu/fpt/savora/repository/RoleRepository.java).
   - Frontend: [`login.html`](src/main/resources/static/public/auth/login.html), [`view-home.html`](src/main/resources/static/public/home/view-home.html), [`login.css`](src/main/resources/static/assets/css/public/login.css), [`auth.js`](src/main/resources/static/assets/js/public/auth.js), [`api.js`](src/main/resources/static/assets/js/common/api.js).

## 2. Cấu trúc thư mục để các thành viên trong nhóm code tiếp và push Git
Các thư mục phân hệ (`admin/`, `staff/`, `customer/`, `public/menu/`, `public/reservation/`) đã được tạo sẵn kèm file `.gitkeep` để từng thành viên nhận task, viết code và push commit riêng lên Git:
- `src/main/java/vn/edu/fpt/savora/controller/{admin,customer,staff}/`
- `src/main/resources/static/{admin,customer,staff}/`
- `src/main/resources/static/public/{menu,reservation}/`
- `src/main/resources/static/assets/css/{admin,customer,staff}/`
- `src/main/resources/static/assets/js/{admin,customer,staff}/`
