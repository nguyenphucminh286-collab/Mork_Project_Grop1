package vn.edu.fpt.savora.security.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * BCrypt password hashing and verification utility.
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
        // Supports both BCrypt hashes and plain-text passwords inserted directly in MySQL Workbench
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
