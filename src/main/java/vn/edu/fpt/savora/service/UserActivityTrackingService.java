package vn.edu.fpt.savora.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.savora.dto.response.AuthUserResponse;
import vn.edu.fpt.savora.entity.UserActivityLog;
import vn.edu.fpt.savora.repository.UserActivityLogRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserActivityTrackingService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserActivityLogRepository userActivityLogRepository;

    @Transactional
    public void recordUserLogin(AuthUserResponse authUser, HttpServletRequest req) {
        if (authUser == null) return;
        try {
            ensureSeededIfEmpty();
            String ip = extractClientIp(req);
            String location = resolveLocation(req, ip);
            String device = parseDeviceInfo(req != null ? req.getHeader("User-Agent") : null);
            String targetPortal = authUser.getRedirectUrl() != null ? authUser.getRedirectUrl() : "/home";

            UserActivityLog logEntry = UserActivityLog.builder()
                    .userId(authUser.getUserId())
                    .userEmail(authUser.getEmail())
                    .userFullName(authUser.getFullName())
                    .userRole(authUser.getRoleName() != null ? authUser.getRoleName() : "CUSTOMER")
                    .actionType("LOGIN")
                    .pagePath("/login → " + targetPortal)
                    .pageTitle("User Sign-In (" + resolveFriendlyPageTitle(targetPortal) + ")")
                    .ipAddress(ip)
                    .location(location)
                    .deviceInfo(device)
                    .createdAt(LocalDateTime.now())
                    .build();
            userActivityLogRepository.save(logEntry);
        } catch (Exception ignored) {
        }
    }

    @Transactional
    public void recordPageNavigation(HttpServletRequest req) {
        if (req == null || !"GET".equalsIgnoreCase(req.getMethod())) return;
        String path = req.getRequestURI();
        if (path == null || !isTrackablePagePath(path)) return;

        try {
            HttpSession session = req.getSession(true);
            String lastPath = (String) session.getAttribute("__lastTrackedPath");
            Long lastTime = (Long) session.getAttribute("__lastTrackedTime");
            long nowMs = System.currentTimeMillis();
            if (path.equals(lastPath) && lastTime != null && (nowMs - lastTime) < 4000) {
                return;
            }
            session.setAttribute("__lastTrackedPath", path);
            session.setAttribute("__lastTrackedTime", nowMs);

            ensureSeededIfEmpty();

            Integer userId = (Integer) session.getAttribute("userId");
            String fullName = (String) session.getAttribute("fullName");
            String roleName = (String) session.getAttribute("roleName");
            AuthUserResponse authUser = (AuthUserResponse) session.getAttribute("authUser");
            String email = authUser != null ? authUser.getEmail() : null;

            if (userId == null) {
                fullName = path.startsWith("/qr/table/") ? "Dine-In QR Guest" : "Guest Visitor";
                email = "anonymous@guest.savora.vn";
                roleName = "GUEST";
            }

            String ip = extractClientIp(req);
            String location = resolveLocation(req, ip);
            String device = parseDeviceInfo(req.getHeader("User-Agent"));

            UserActivityLog logEntry = UserActivityLog.builder()
                    .userId(userId)
                    .userEmail(email)
                    .userFullName(fullName)
                    .userRole(roleName != null ? roleName : "CUSTOMER")
                    .actionType("PAGE_VISIT")
                    .pagePath(path)
                    .pageTitle(resolveFriendlyPageTitle(path))
                    .ipAddress(ip)
                    .location(location)
                    .deviceInfo(device)
                    .createdAt(LocalDateTime.now())
                    .build();
            userActivityLogRepository.save(logEntry);
        } catch (Exception ignored) {
        }
    }

    @Transactional
    public List<Map<String, Object>> getRecentActivityLogs() {
        ensureSeededIfEmpty();
        return userActivityLogRepository.findTop200ByOrderByCreatedAtDesc().stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("logId", l.getLogId());
            m.put("userId", l.getUserId());
            m.put("userEmail", l.getUserEmail() != null ? l.getUserEmail() : "anonymous@guest.savora.vn");
            m.put("userFullName", l.getUserFullName() != null ? l.getUserFullName() : "Guest Visitor");
            m.put("userRole", l.getUserRole() != null ? l.getUserRole() : "GUEST");
            m.put("actionType", l.getActionType());
            m.put("pagePath", l.getPagePath());
            m.put("pageTitle", l.getPageTitle());
            m.put("ipAddress", l.getIpAddress() != null ? l.getIpAddress() : "127.0.0.1");
            m.put("location", l.getLocation() != null ? l.getLocation() : "Hoa Lac Hi-Tech Park, Hanoi, VN");
            m.put("deviceInfo", l.getDeviceInfo() != null ? l.getDeviceInfo() : "Chrome • Windows 11");
            m.put("createdAt", l.getCreatedAt() != null ? l.getCreatedAt().format(DT_FMT) : "");
            return m;
        }).collect(Collectors.toList());
    }

    private void ensureSeededIfEmpty() {
        if (userActivityLogRepository.count() > 0) return;

        LocalDateTime now = LocalDateTime.now();
        List<UserActivityLog> seeds = List.of(
                UserActivityLog.builder()
                        .userId(2).userEmail("customer@savora.vn").userFullName("Customer Nguyễn Minh").userRole("CUSTOMER")
                        .actionType("LOGIN").pagePath("/login → /home").pageTitle("User Sign-In (Home & Online Table Booking)")
                        .ipAddress("14.162.145.28").location("Hoa Lac Hi-Tech Park, Hanoi, VN")
                        .deviceInfo("Chrome 131 • Windows 11").createdAt(now.minusMinutes(48)).build(),
                UserActivityLog.builder()
                        .userId(2).userEmail("customer@savora.vn").userFullName("Customer Nguyễn Minh").userRole("CUSTOMER")
                        .actionType("PAGE_VISIT").pagePath("/home").pageTitle("Home & Online Table Reservation")
                        .ipAddress("14.162.145.28").location("Hoa Lac Hi-Tech Park, Hanoi, VN")
                        .deviceInfo("Chrome 131 • Windows 11").createdAt(now.minusMinutes(46)).build(),
                UserActivityLog.builder()
                        .userId(2).userEmail("customer@savora.vn").userFullName("Customer Nguyễn Minh").userRole("CUSTOMER")
                        .actionType("PAGE_VISIT").pagePath("/customer/reservations").pageTitle("Customer Booking History")
                        .ipAddress("14.162.145.28").location("Hoa Lac Hi-Tech Park, Hanoi, VN")
                        .deviceInfo("Chrome 131 • Windows 11").createdAt(now.minusMinutes(42)).build(),
                UserActivityLog.builder()
                        .userId(null).userEmail("anonymous@guest.savora.vn").userFullName("Dine-In QR Guest").userRole("GUEST")
                        .actionType("PAGE_VISIT").pagePath("/qr/table/T1-B01").pageTitle("Table QR Digital Menu (Table T1-B01)")
                        .ipAddress("113.190.232.14").location("Savora Dining Hall (1st Floor), Hanoi, VN")
                        .deviceInfo("Safari • iPhone iOS 18").createdAt(now.minusMinutes(35)).build(),
                UserActivityLog.builder()
                        .userId(4).userEmail("chef@savora.vn").userFullName("Chef Hoang Le").userRole("CHEF")
                        .actionType("LOGIN").pagePath("/login → /staff/kitchen").pageTitle("User Sign-In (Chef Kitchen Display KDS)")
                        .ipAddress("192.168.1.40").location("Savora Executive Kitchen, Hoa Lac, Hanoi, VN")
                        .deviceInfo("Edge • Kitchen Touch Display").createdAt(now.minusMinutes(30)).build(),
                UserActivityLog.builder()
                        .userId(4).userEmail("chef@savora.vn").userFullName("Chef Hoang Le").userRole("CHEF")
                        .actionType("PAGE_VISIT").pagePath("/staff/kitchen").pageTitle("Chef Kitchen Display System (KDS)")
                        .ipAddress("192.168.1.40").location("Savora Executive Kitchen, Hoa Lac, Hanoi, VN")
                        .deviceInfo("Edge • Kitchen Touch Display").createdAt(now.minusMinutes(29)).build(),
                UserActivityLog.builder()
                        .userId(3).userEmail("staff@savora.vn").userFullName("Waiter Minh Anh").userRole("STAFF")
                        .actionType("LOGIN").pagePath("/login → /staff/tables").pageTitle("User Sign-In (Waiter Floor & Order Portal)")
                        .ipAddress("192.168.1.25").location("Savora Service Counter, Hoa Lac, Hanoi, VN")
                        .deviceInfo("Chrome 131 • Windows 11").createdAt(now.minusMinutes(22)).build(),
                UserActivityLog.builder()
                        .userId(3).userEmail("staff@savora.vn").userFullName("Waiter Minh Anh").userRole("STAFF")
                        .actionType("PAGE_VISIT").pagePath("/staff/tables").pageTitle("Waiter Floor & Table Order Management")
                        .ipAddress("192.168.1.25").location("Savora Service Counter, Hoa Lac, Hanoi, VN")
                        .deviceInfo("Chrome 131 • Windows 11").createdAt(now.minusMinutes(21)).build(),
                UserActivityLog.builder()
                        .userId(1).userEmail("admin@savora.vn").userFullName("Admin Savora").userRole("ADMIN")
                        .actionType("LOGIN").pagePath("/login → /admin/dashboard").pageTitle("User Sign-In (Executive Admin Portal)")
                        .ipAddress("127.0.0.1").location("Hoa Lac Hi-Tech Park, Hanoi, VN")
                        .deviceInfo("Chrome 131 • Windows 11").createdAt(now.minusMinutes(10)).build(),
                UserActivityLog.builder()
                        .userId(1).userEmail("admin@savora.vn").userFullName("Admin Savora").userRole("ADMIN")
                        .actionType("PAGE_VISIT").pagePath("/admin/dashboard").pageTitle("Executive Admin Operations & Tracking Dashboard")
                        .ipAddress("127.0.0.1").location("Hoa Lac Hi-Tech Park, Hanoi, VN")
                        .deviceInfo("Chrome 131 • Windows 11").createdAt(now.minusMinutes(9)).build()
        );
        userActivityLogRepository.saveAll(seeds);
    }

    private boolean isTrackablePagePath(String path) {
        if (path.startsWith("/api/")
                || path.startsWith("/assets/")
                || path.startsWith("/components/")
                || path.startsWith("/error")
                || path.startsWith("/favicon")
                || path.contains(".")) {
            return false;
        }
        return true;
    }

    private String resolveFriendlyPageTitle(String path) {
        if (path == null) return "Savora Application Page";
        if ("/".equals(path) || "/home".equals(path)) return "Home & Online Table Reservation";
        if ("/login".equals(path)) return "Account Sign-In Page";
        if ("/register".equals(path)) return "Customer Registration Page";
        if ("/contact".equals(path)) return "Restaurant Contact & Location Page";
        if ("/customer/reservations".equals(path)) return "Customer Booking History";
        if ("/customer/profile".equals(path)) return "Customer Account Profile";
        if (path.startsWith("/qr/table/")) {
            String code = path.substring("/qr/table/".length());
            return "Table QR Digital Menu (Table " + code.toUpperCase() + ")";
        }
        if ("/qr/order".equals(path)) return "Table QR Digital Menu";
        if ("/staff".equals(path) || "/staff/tables".equals(path)) return "Waiter Floor & Table Order Portal";
        if ("/chef".equals(path) || "/staff/kitchen".equals(path)) return "Chef Kitchen Display System (KDS)";
        if ("/staff/profile".equals(path)) return "Staff Account Profile";
        if ("/admin".equals(path) || "/admin/dashboard".equals(path)) return "Executive Admin Control Center";
        return "App Route (" + path + ")";
    }

    private String extractClientIp(HttpServletRequest req) {
        if (req == null) return "127.0.0.1";
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = req.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String remote = req.getRemoteAddr();
        if ("0:0:0:0:0:0:0:1".equals(remote) || "::1".equals(remote)) {
            return "127.0.0.1";
        }
        return remote != null ? remote : "127.0.0.1";
    }

    private String resolveLocation(HttpServletRequest req, String ip) {
        if (req != null) {
            String cfCity = req.getHeader("CF-IPCity");
            String cfCountry = req.getHeader("CF-IPCountry");
            if (cfCity != null && !cfCity.isBlank()) {
                return cfCity + (cfCountry != null ? ", " + cfCountry : "");
            }
        }
        if ("127.0.0.1".equals(ip) || ip.startsWith("192.168.") || ip.startsWith("10.")) {
            return "Hoa Lac Hi-Tech Park, Hanoi, VN (Local Network)";
        }
        return "Hanoi, Vietnam (" + ip + ")";
    }

    private String parseDeviceInfo(String ua) {
        if (ua == null || ua.isBlank()) return "Web Browser • Desktop";
        String browser = "Browser";
        if (ua.contains("Edg/")) browser = "Microsoft Edge";
        else if (ua.contains("Chrome/") && !ua.contains("Edg/")) browser = "Google Chrome";
        else if (ua.contains("Firefox/")) browser = "Mozilla Firefox";
        else if (ua.contains("Safari/") && !ua.contains("Chrome/")) browser = "Apple Safari";

        String os = "Desktop";
        if (ua.contains("Windows NT 10.0")) os = "Windows 10/11";
        else if (ua.contains("iPhone") || ua.contains("iPad")) os = "iOS Mobile";
        else if (ua.contains("Android")) os = "Android Mobile";
        else if (ua.contains("Mac OS X")) os = "macOS";
        else if (ua.contains("Linux")) os = "Linux";

        return browser + " • " + os;
    }
}
