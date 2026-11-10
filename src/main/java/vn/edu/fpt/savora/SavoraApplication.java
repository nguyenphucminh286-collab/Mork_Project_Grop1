package vn.edu.fpt.savora;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Điểm khởi chạy chính của hệ thống Quản lý Nhà hàng Savora.
 * Chạy trực tiếp trên VS Code (nhấn F5 hoặc Run) với Embedded Tomcat.
 * Không cần cài đặt hay cấu hình Tomcat ngoài như trên IntelliJ.
 */
@SpringBootApplication
@EnableScheduling
public class SavoraApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(SavoraApplication.class, args);
        Environment env = context.getEnvironment();
        String port = env.getProperty("server.port", "8083");
        System.out.println("===============================================================");
        System.out.println("  SAVORA RESTAURANT SYSTEM IS RUNNING AT: http://localhost:" + port);
        System.out.println("===============================================================");
    }
}
