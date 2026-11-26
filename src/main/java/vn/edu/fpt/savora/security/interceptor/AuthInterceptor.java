package vn.edu.fpt.savora.security.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import vn.edu.fpt.savora.entity.User;
import vn.edu.fpt.savora.enums.UserStatus;
import vn.edu.fpt.savora.repository.UserRepository;
import vn.edu.fpt.savora.service.UserActivityTrackingService;

import java.io.IOException;

/**
 * Authentication and role-based access control interceptor.
 * Role ID conventions:
 * - roleId = 1: ADMIN (Administrator / Restaurant Owner)
 * - roleId = 2: CUSTOMER (Customer)
 * - roleId = 3: STAFF (Waiter / Cashier)
 * - roleId = 4: CHEF (Kitchen Chef)
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final UserActivityTrackingService userActivityTrackingService;
    private final UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) throws IOException {
        String path = req.getRequestURI();

        // Allow CORS preflight OPTIONS requests and static resources
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())
                || path.startsWith("/assets/")
                || path.startsWith("/components/")
                || path.startsWith("/public/")
                || path.endsWith(".css")
                || path.endsWith(".js")
                || path.endsWith(".png")
                || path.endsWith(".jpg")
                || path.endsWith(".webp")
                || path.endsWith(".svg")
                || path.endsWith(".ico")) {
            return true;
        }

        HttpSession session = req.getSession(false);
        Integer userId = session != null ? (Integer) session.getAttribute("userId") : null;
        Integer roleId = session != null ? (Integer) session.getAttribute("roleId") : null;

        // 0. Check real-time account lock status (including Google OAuth2 accounts)
        if (userId != null) {
            User currentUser = userRepository.findById(userId).orElse(null);
            if (currentUser == null || currentUser.getStatus() == UserStatus.LOCKED) {
                session.invalidate();
                if (path.startsWith("/api/")) {
                    resp.setContentType("application/json;charset=UTF-8");
                    resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    resp.getWriter().write("{\"success\":false,\"message\":\"Your account has been locked by the Administrator.\",\"data\":null}");
                } else {
                    resp.sendRedirect("/login?locked=true");
                }
                return false;
            }
        }

        // 1. ADMIN authorization (roleId = 1)
        if (path.startsWith("/admin/") || path.startsWith("/api/admin/")) {
            if (userId == null) {
                return handleUnauthorized(req, resp);
            }
            if (roleId == null || roleId != 1) {
                return handleForbidden(req, resp);
            }
        }

        // 2. STAFF / CHEF authorization (roleId = 3 or 4, or ADMIN = 1)
        if (path.startsWith("/staff/") || path.startsWith("/api/staff/")) {
            if (userId == null) {
                return handleUnauthorized(req, resp);
            }
            if (roleId == null || (roleId != 1 && roleId != 3 && roleId != 4)) {
                return handleForbidden(req, resp);
            }
        }

        // 3. CUSTOMER authorization (requires authenticated session)
        if (path.startsWith("/customer/") || path.startsWith("/api/customer/")) {
            if (userId == null) {
                return handleUnauthorized(req, resp);
            }
        }

        // 4. Record page navigation history for the Admin Tracking Dashboard
        userActivityTrackingService.recordPageNavigation(req);

        return true;
    }

    private boolean handleUnauthorized(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (req.getRequestURI().startsWith("/api/")) {
            resp.setContentType("application/json;charset=UTF-8");
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"success\":false,\"message\":\"Please sign in to continue.\",\"data\":null}");
        } else {
            resp.sendRedirect("/login");
        }
        return false;
    }

    private boolean handleForbidden(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (req.getRequestURI().startsWith("/api/")) {
            resp.setContentType("application/json;charset=UTF-8");
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.getWriter().write("{\"success\":false,\"message\":\"You do not have permission to access this resource.\",\"data\":null}");
        } else {
            resp.sendRedirect("/home");
        }
        return false;
    }
}
