package vn.edu.fpt.savora.service;

import com.cloudinary.Cloudinary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Boot Service upload ảnh lên Cloudinary (Nâng cấp từ UploadImage.java của SE2034-SWP391-G6)
 */
@Service
@RequiredArgsConstructor
public class CloudinaryUploadService {

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024; // 5MB

    private final Cloudinary cloudinary;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public String uploadImage(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please select an image file to upload.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Only image files (JPG, PNG, JPEG, GIF, WEBP) are allowed.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Image size exceeds the maximum limit of 5MB.");
        }

        Map options = new HashMap();
        options.put("folder", (folder != null && !folder.isBlank()) ? folder.trim() : "savora/dishes");
        options.put("use_filename", true);
        options.put("unique_filename", true);

        try {
            byte[] fileBytes = file.getBytes();
            Map uploadResult = cloudinary.uploader().upload(fileBytes, options);
            Object secureUrl = uploadResult.get("secure_url");
            if (secureUrl == null) {
                secureUrl = uploadResult.get("url");
            }
            return String.valueOf(secureUrl);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to upload image to Cloudinary: " + e.getMessage());
        }
    }
}
