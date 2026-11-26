package vn.edu.fpt.savora.config;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring Boot Cloudinary Configuration (migrated from UploadImage.java static config).
 */
@Configuration
public class CloudinaryConfig {

    @Value("${cloudinary.cloud-name:dej5mxdrt}")
    private String cloudName;

    @Value("${cloudinary.api-key:499611776514839}")
    private String apiKey;

    @Value("${cloudinary.api-secret:s00278LAWCsjJt2cNJfpUCBI2Kg}")
    private String apiSecret;

    @Bean
    public Cloudinary cloudinary() {
        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", cloudName != null ? cloudName.trim() : "");
        config.put("api_key", apiKey != null ? apiKey.trim() : "");
        config.put("api_secret", apiSecret != null ? apiSecret.trim() : "");
        config.put("secure", "true");
        return new Cloudinary(config);
    }
}
