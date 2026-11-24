package com.netguard.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web MVC 配置：把设备图片上传目录映射为静态资源，供页面直接访问。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(appProperties.getUpload().getDir()).toAbsolutePath().normalize();
        String location = uploadPath.toUri().toString();
        registry.addResourceHandler(appProperties.getUpload().getAccessPath() + "**")
                .addResourceLocations(location);
    }
}
