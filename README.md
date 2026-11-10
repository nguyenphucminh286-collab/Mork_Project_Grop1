# Savora - Restaurant Management System

**Savora** is a web-based Restaurant Management System built with **Java 17 + Spring Boot 3.2.5 + MySQL 8.0 + Pure HTML/CSS/JS** and configured to run seamlessly on **VS Code**.

## 1. Initial Setup & Authentication Module
1. **Environment & Configuration**:
   - [`pom.xml`](pom.xml): Java 17, Spring Boot Web, Spring Data JPA, Spring Security OAuth2 Client (Google & Facebook Login), MySQL Driver, Lombok, jBCrypt.
   - [`application.properties`](src/main/resources/application.properties): MySQL (`savora_db`) configuration, Google & Facebook OAuth2 Client configuration, running on port `8083`.
   - [`database/savora_mysql_schema.sql`](database/savora_mysql_schema.sql): Complete MySQL 8.0 database schema (14 tables, triggers, indexes, and sample seed data).
   - [`.vscode/`](.vscode/launch.json): One-click Run & Debug configuration for VS Code.
2. **Shared JPA Entities & Enums**:
   - `vn.edu.fpt.savora.model.*`: 13 JPA Entities (`User`, `Role`, `Category`, `Dish`, `DishImage`, `DiningTable`, `Reservation`, `RestaurantOrder`, `OrderItem`, `Payment`, `Voucher`, `Review`, `EmailVerification`).
   - `vn.edu.fpt.savora.enums.*`: 11 status and type Enums.
3. **Authentication Module (Email/Password + Google OAuth2 + Facebook OAuth2)**:
   - Backend: [`SecurityConfig.java`](src/main/java/vn/edu/fpt/savora/config/SecurityConfig.java), [`AuthController.java`](src/main/java/vn/edu/fpt/savora/controller/auth/AuthController.java), [`AuthService.java`](src/main/java/vn/edu/fpt/savora/service/AuthService.java), [`UserRepository.java`](src/main/java/vn/edu/fpt/savora/repository/UserRepository.java), [`RoleRepository.java`](src/main/java/vn/edu/fpt/savora/repository/RoleRepository.java).
   - Frontend: [`login.html`](src/main/resources/static/public/auth/login.html), [`view-home.html`](src/main/resources/static/public/home/view-home.html), [`login.css`](src/main/resources/static/assets/css/public/login.css), [`auth.js`](src/main/resources/static/assets/js/public/auth.js), [`api.js`](src/main/resources/static/assets/js/common/api.js).

## 2. Module Directory Structure for Team Collaboration
Dedicated package and static folders (`admin/`, `staff/`, `customer/`, `public/menu/`, `public/reservation/`) are pre-initialized with `.gitkeep` so each team member can develop and push their assigned features:
- `src/main/java/vn/edu/fpt/savora/controller/{admin,customer,staff}/`
- `src/main/resources/static/{admin,customer,staff}/`
- `src/main/resources/static/public/{menu,reservation}/`
- `src/main/resources/static/assets/css/{admin,customer,staff}/`
- `src/main/resources/static/assets/js/{admin,customer,staff}/`
