package vn.edu.fpt.savora.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Bộ lọc kiểm tra đăng nhập và phân quyền theo Role (Nâng cấp từ AuthFilter.java của SE2034-SWP391-G6).
 * Role quy ước:
 * - roleId = 1: ADMIN (Quản trị viên / Chủ nhà hàng)
 * - roleId = 2: CUSTOMER (Khách hàng)
 * - roleId = 3: STAFF (Nhân viên phục vụ / Thu ngân)
 * - roleId = 4: CHEF (Nhân viên bếp)
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) throws IOException {
        String path = req.getRequestURI();

        // Cho phép preflight OPTIONS request (CORS) và tài nguyên tĩnh
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

        // 1. Phân quyền ADMIN (roleId = 1)
        if (path.startsWith("/admin/") || path.startsWith("/api/admin/")) {
            if (userId == null) {
                return handleUnauthorized(req, resp);
            }
            if (roleId == null || roleId != 1) {
                return handleForbidden(req, resp);
            }
        }

        // 2. Phân quyền STAFF / CHEF (roleId = 3 hoặc 4, hoặc ADMIN = 1)
        if (path.startsWith("/staff/") || path.startsWith("/api/staff/")) {
            if (userId == null) {
                return handleUnauthorized(req, resp);
            }
            if (roleId == null || (roleId != 1 && roleId != 3 && roleId != 4)) {
                return handleForbidden(req, resp);
            }
        }

        // 3. Phân quyền CUSTOMER (Yêu cầu đăng nhập)
        if (path.startsWith("/customer/") || path.startsWith("/api/customer/")) {
            if (userId == null) {
                return handleUnauthorized(req, resp);
            }
        }

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
