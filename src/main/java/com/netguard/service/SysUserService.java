package com.netguard.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.netguard.dto.PasswordForm;
import com.netguard.dto.ProfileForm;
import com.netguard.entity.SysUser;
import com.netguard.exception.BusinessException;
import com.netguard.mapper.SysUserMapper;
import com.netguard.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户服务：个人资料维护与密码修改。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    public SysUser getById(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    public SysUser getByUsername(String username) {
        return sysUserMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
    }

    /**
     * 全部启用用户，供演示角色切换与邮件收件人选择。
     */
    public List<SysUser> listEnabled() {
        return sysUserMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getId));
    }

    /**
     * 更新当前登录用户的资料。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(ProfileForm form) {
        Long userId = SecurityUtils.currentUserId();
        if (userId == null) {
            throw new BusinessException("登录状态已失效，请重新登录");
        }
        SysUser user = getById(userId);
        user.setRealName(form.getRealName().trim());
        user.setEmail(form.getEmail());
        user.setPhone(form.getPhone());
        sysUserMapper.updateById(user);
        log.info("用户 {} 更新了个人资料", user.getUsername());
    }

    /**
     * 修改密码：校验原密码、两次输入一致，并使用 BCrypt 加密存储。
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(PasswordForm form) {
        Long userId = SecurityUtils.currentUserId();
        if (userId == null) {
            throw new BusinessException("登录状态已失效，请重新登录");
        }
        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            throw new BusinessException("两次输入的新密码不一致");
        }
        if (form.getNewPassword().equals(form.getOldPassword())) {
            throw new BusinessException("新密码不能与原密码相同");
        }
        SysUser user = getById(userId);
        if (!passwordEncoder.matches(form.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码不正确");
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setPassword(passwordEncoder.encode(form.getNewPassword()));
        sysUserMapper.updateById(update);
        log.info("用户 {} 修改了登录密码", user.getUsername());
    }
}
