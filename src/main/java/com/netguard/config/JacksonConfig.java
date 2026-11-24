package com.netguard.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 全局序列化配置。
 *
 * <p>统一 Java 8 时间类型的 JSON 输出格式，避免接口返回 ISO-8601
 * （如 {@code 2026-11-24T01:34:21.382}）与页面展示格式不一致。</p>
 *
 * <ul>
 *   <li>LocalDateTime → {@code yyyy-MM-dd HH:mm:ss}</li>
 *   <li>LocalDate → {@code yyyy-MM-dd}</li>
 *   <li>LocalTime → {@code HH:mm:ss}</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final String TIME_PATTERN = "HH:mm:ss";

    /**
     * 注册时间类型的序列化器与反序列化器。
     *
     * @return Spring Boot Jackson 构建器定制器
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer javaTimeCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DATE_PATTERN);
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(TIME_PATTERN);

            module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dateTimeFormatter));
            module.addSerializer(LocalDate.class, new LocalDateSerializer(dateFormatter));
            module.addSerializer(LocalTime.class, new LocalTimeSerializer(timeFormatter));
            module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(dateTimeFormatter));
            module.addDeserializer(LocalDate.class, new LocalDateDeserializer(dateFormatter));
            module.addDeserializer(LocalTime.class, new LocalTimeDeserializer(timeFormatter));

            builder.modules(module);
        };
    }
}
