package com.netguard.service;

import com.netguard.common.ResultCode;
import com.netguard.config.AppProperties;
import com.netguard.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 文件上传服务：保存设备图片，返回可直接访问的相对路径。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "gif");
    private static final long MAX_SIZE = 5 * 1024 * 1024L;

    private final AppProperties appProperties;

    private Path uploadRoot;

    @PostConstruct
    public void init() {
        uploadRoot = Paths.get(appProperties.getUpload().getDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadRoot);
            log.info("设备图片上传目录：{}", uploadRoot);
        } catch (IOException e) {
            log.error("创建上传目录失败：{}", e.getMessage());
        }
    }

    /**
     * 保存上传的图片。
     *
     * @param file 上传文件，可为空（表示不修改图片）
     * @return 可访问的相对路径，如 /uploads/2026/11/xxx.png；未上传时返回 null
     */
    public String storeDeviceImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(ResultCode.FILE_UPLOAD_ERROR.getCode(), "图片大小不能超过 5MB");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED);
        }
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        try {
            Path dir = uploadRoot.resolve(datePath);
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(fileName).toFile());
            return appProperties.getUpload().getAccessPath() + datePath + "/" + fileName;
        } catch (IOException e) {
            log.error("保存设备图片失败：{}", e.getMessage());
            throw new BusinessException(ResultCode.FILE_UPLOAD_ERROR.getCode(), "图片保存失败，请稍后重试");
        }
    }
}
