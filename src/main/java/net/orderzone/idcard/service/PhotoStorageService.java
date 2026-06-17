package net.orderzone.idcard.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class PhotoStorageService {

    @Value("${upload.photo-dir}")
    private String uploadDir;

    private Path rootLocation;

    // Allowed image formats
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/png", "image/jpg");

    @PostConstruct
    public void init() {
        try {
            this.rootLocation = Paths.get(uploadDir);
            // Automatically creates the uploads/photos folder if it doesn't exist yet
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage directory for photos!", e);
        }
    }

    /**
     * Validates and stores an incoming file, returning its unique generated filename.
     */
    public String storePhoto(MultipartFile file) {
        // 1. Validation: Ensure file is not empty
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Failed to store empty file.");
        }

        // 2. Validation: Strict File Type checking (JPEG or PNG only)
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Invalid file type! Only JPEG and PNG images are allowed.");
        }

        try {
            // 3. Generate a unique file name to avoid duplicates (e.g., e4f82a91-abc.png)
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String uniqueFilename = UUID.randomUUID().toString() + extension;

            // 4. Save to the local filesystem target destination
            Path destinationFile = this.rootLocation.resolve(Paths.get(uniqueFilename))
                    .normalize().toAbsolutePath();

            // Security check: Guard against path traversal attacks (e.g., filenames with "../../")
            if (!destinationFile.getParent().equals(this.rootLocation.toAbsolutePath())) {
                throw new SecurityException("Cannot store file outside current directory.");
            }

            Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);

            return uniqueFilename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store photo file on disk.", e);
        }
    }
}