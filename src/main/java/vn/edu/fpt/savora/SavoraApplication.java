package vn.edu.fpt.savora;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Savora Restaurant Management System.
 * Runs directly in VS Code (F5 or Run) with Embedded Tomcat,
 * without requiring external Tomcat installation or configuration.
 */
@SpringBootApplication
@EnableScheduling
public class SavoraApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(SavoraApplication.class, args);
        Environment env = context.getEnvironment();
        String port = env.getProperty("server.port", "8080");
        System.out.println("===============================================================");
        System.out.println("  SAVORA RESTAURANT SYSTEM IS RUNNING AT: http://localhost:" + port);
        System.out.println("===============================================================");
    }
}
