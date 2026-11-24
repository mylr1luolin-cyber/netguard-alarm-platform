package com.netguard.service;

import com.netguard.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.List;

/**
 * 邮件发送服务，基于 JavaMailSender 发送 HTML 告警邮件。
 *
 * <p>当 spring.mail.host 未配置或 app.mail.enabled=false 时自动跳过发送，
 * 并通过返回值告知调用方，便于写入“未发送”的通知记录。</p>
 */
@Slf4j
@Service
public class MailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final AppProperties appProperties;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    public MailService(ObjectProvider<JavaMailSender> mailSenderProvider, AppProperties appProperties) {
        this.mailSenderProvider = mailSenderProvider;
        this.appProperties = appProperties;
    }

    /**
     * 邮件通道是否可用（开关打开且邮件服务器已配置）。
     */
    public boolean isAvailable() {
        return appProperties.getMail().isEnabled()
                && mailSenderProvider.getIfAvailable() != null
                && fromAddress != null && !fromAddress.isEmpty();
    }

    /**
     * 发送 HTML 邮件。
     *
     * @param recipients 收件人列表
     * @param subject    主题
     * @param html       正文（HTML）
     * @return 是否发送成功
     */
    public boolean sendHtml(List<String> recipients, String subject, String html) {
        if (!isAvailable()) {
            log.warn("邮件通道未启用或未配置，跳过发送：subject={}", subject);
            return false;
        }
        if (recipients == null || recipients.isEmpty()) {
            log.warn("收件人为空，跳过发送：subject={}", subject);
            return false;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            return false;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, appProperties.getMail().getFromName());
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
            log.info("告警邮件发送成功，收件人={}，主题={}", recipients, subject);
            return true;
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("告警邮件发送失败：{}", e.getMessage());
            return false;
        } catch (RuntimeException e) {
            log.error("告警邮件发送异常：{}", e.getMessage());
            return false;
        }
    }
}
