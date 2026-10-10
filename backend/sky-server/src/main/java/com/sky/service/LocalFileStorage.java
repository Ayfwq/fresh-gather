package com.sky.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/** Stores uploaded product photos on the application server's persistent disk. */
@Service
public class LocalFileStorage {

    private static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;
    private static final java.util.Set<String> ALLOWED_EXTENSIONS = Arrays.stream(
                    new String[]{".jpg", ".jpeg", ".png", ".gif", ".webp"})
            .collect(Collectors.toSet());

    private final Path root;

    public LocalFileStorage(@Value("${sky.upload.local-dir:.runtime/uploads}") String directory) {
        this.root = Paths.get(directory).toAbsolutePath().normalize();
    }

    public String store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null) {
            throw new IllegalArgumentException("请选择图片文件");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("图片不能超过 10 MB");
        }

        String originalName = file.getOriginalFilename();
        int extensionIndex = originalName.lastIndexOf('.');
        if (extensionIndex <= 0) {
            throw new IllegalArgumentException("图片格式不支持");
        }
        String extension = originalName.substring(extensionIndex).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("仅支持 JPG、PNG、GIF 或 WebP 图片");
        }
        if (!hasExpectedImageSignature(file, extension)) {
            throw new IllegalArgumentException("文件内容与图片格式不匹配");
        }

        Files.createDirectories(root);
        String storedName = UUID.randomUUID() + extension;
        Path destination = root.resolve(storedName).normalize();
        if (!destination.getParent().equals(root)) {
            throw new IllegalArgumentException("图片文件名无效");
        }
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, destination);
        }
        return "/uploads/" + storedName;
    }

    private boolean hasExpectedImageSignature(MultipartFile file, String extension) throws IOException {
        byte[] header = new byte[12];
        int read = 0;
        try (InputStream input = file.getInputStream()) {
            while (read < header.length) {
                int count = input.read(header, read, header.length - read);
                if (count < 0) {
                    break;
                }
                read += count;
            }
        }

        switch (extension) {
            case ".png":
                return read >= 8 && (header[0] & 0xff) == 0x89 && header[1] == 'P'
                        && header[2] == 'N' && header[3] == 'G' && header[4] == 0x0d
                        && header[5] == 0x0a && header[6] == 0x1a && header[7] == 0x0a;
            case ".jpg":
            case ".jpeg":
                return read >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8
                        && (header[2] & 0xff) == 0xff;
            case ".gif":
                return read >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F'
                        && header[3] == '8' && (header[4] == '7' || header[4] == '9') && header[5] == 'a';
            case ".webp":
                return read >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F'
                        && header[3] == 'F' && header[8] == 'W' && header[9] == 'E'
                        && header[10] == 'B' && header[11] == 'P';
            default:
                return false;
        }
    }

    public Path getRoot() {
        return root;
    }
}
