package com.netguard.config;

import com.netguard.security.LoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 安全配置。
 *
 * <p>权限模型：</p>
 * <ul>
 *   <li>未登录只能访问登录页与静态资源；</li>
 *   <li>ADMIN：设备增删改、设备类型维护、告警规则配置、用户查看；</li>
 *   <li>OPS：查看设备状态、查看与确认告警、处理故障、查看通知。</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final LoginSuccessHandler loginSuccessHandler;

    /**
     * 密码加密器：BCrypt 自带随机盐，数据库中不保存明文密码。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeRequests()
                // 登录页与静态资源放行
                .antMatchers("/login", "/css/**", "/js/**", "/images/**", "/fonts/**",
                        "/webjars/**", "/uploads/**", "/favicon.ico", "/error", "/health").permitAll()
                // 设备类型维护、规则配置、用户管理仅管理员
                .antMatchers("/types/**", "/rule/**", "/users/**").hasRole("ADMIN")
                // 设备写操作（新增、编辑、删除、手动巡检触发）仅管理员
                .antMatchers(HttpMethod.POST, "/devices/**").hasRole("ADMIN")
                .antMatchers(HttpMethod.POST, "/inspect/run").hasRole("ADMIN")
                // 其余请求需登录（含告警确认，管理员与运维均可）
                .anyRequest().authenticated()
                .and()
                .formLogin()
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler(loginSuccessHandler)
                .failureUrl("/login?error=true")
                .permitAll()
                .and()
                .logout()
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .and()
                .exceptionHandling()
                // 已登录但权限不足时跳转 403 页面（防越权访问）
                .accessDeniedPage("/403")
                .and()
                .sessionManagement()
                .maximumSessions(1)
                .maxSessionsPreventsLogin(false)
                .expiredUrl("/login?expired=true")
                .and()
                .and()
                // 允许同源 iframe（告警详情弹窗）
                .headers()
                .frameOptions().sameOrigin();
        return http.build();
    }
}
