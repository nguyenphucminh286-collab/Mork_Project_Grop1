package vn.edu.fpt.savora.common;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Tiện ích mã hóa và kiểm tra mật khẩu BCrypt (Kế thừa từ PasswordUtils.java của SE2034-SWP391-G6)
 */
public final class PasswordUtils {

    private PasswordUtils() {
    }

    public static String hashPassword(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(10));
    }

    public static boolean checkPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null || hashedPassword.isBlank()) {
            return false;
        }
        // Hỗ trợ kiểm tra cả chuỗi đã băm BCrypt lẫn mật khẩu thuần (nếu nhập trực tiếp trong MySQL Workbench)
        if (hashedPassword.startsWith("$2a$") || hashedPassword.startsWith("$2b$") || hashedPassword.startsWith("$2y$")) {
            try {
                return BCrypt.checkpw(plainPassword, hashedPassword);
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }
        return plainPassword.equals(hashedPassword);
    }
}
