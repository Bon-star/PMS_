package com.example.pms.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProjectRequirementFileService {

    public static final long MAX_UPLOAD_BYTES = 10L * 1024L * 1024L;

    private static final String UPLOAD_DIR = "uploads/project-requirements";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx",
            "txt", "md", "zip", "rar", "7z",
            "png", "jpg", "jpeg", "webp");

    public static class StoredFile {
        private final String storedPath;
        private final String originalFileName;

        public StoredFile(String storedPath, String originalFileName) {
            this.storedPath = storedPath;
            this.originalFileName = originalFileName;
        }

        public String getStoredPath() {
            return storedPath;
        }

        public String getOriginalFileName() {
            return originalFileName;
        }
    }

    public String normalizeFileName(String originalName) {
        String safe = originalName == null ? "file" : Paths.get(originalName).getFileName().toString();
        safe = safe.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.length() > 120) {
            safe = safe.substring(0, 120);
        }
        if (safe.isBlank()) {
            safe = "file";
        }
        return safe;
    }

    public String fileExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    public boolean isAllowedExtension(String fileName) {
        return ALLOWED_EXTENSIONS.contains(fileExtension(fileName));
    }

    public StoredFile store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("Requirement file is empty.");
        }

        String originalName = normalizeFileName(file.getOriginalFilename());
        String extension = fileExtension(originalName);
        String storedName = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);

        Path uploadDir = Paths.get(UPLOAD_DIR).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);

        Path destination = uploadDir.resolve(storedName).normalize();
        if (!destination.startsWith(uploadDir)) {
            throw new IOException("Invalid requirement file path.");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        }

        return new StoredFile(storedName, originalName);
    }

    public Resource loadAsResource(String storedPath) throws IOException {
        if (storedPath == null || storedPath.isBlank()) {
            throw new IOException("Requirement file path is empty.");
        }

        Path uploadDir = Paths.get(UPLOAD_DIR).toAbsolutePath().normalize();
        Path filePath = uploadDir.resolve(storedPath).normalize();
        if (!filePath.startsWith(uploadDir) || !Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new IOException("Requirement file not found.");
        }

        return new PathResource(filePath);
    }
}
