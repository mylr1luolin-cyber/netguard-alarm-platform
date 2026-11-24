package com.netguard.controller;

import com.netguard.dto.PasswordForm;
import com.netguard.dto.ProfileForm;
import com.netguard.entity.SysUser;
import com.netguard.security.SecurityUtils;
import com.netguard.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;

/**
 * 个人中心控制器：资料维护与密码修改。
 */
@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final SysUserService sysUserService;

    /**
     * 个人中心页面。
     */
    @GetMapping
    public String profile(Model model) {
        SysUser user = sysUserService.getById(SecurityUtils.currentUserId());
        if (!model.containsAttribute("profileForm")) {
            ProfileForm form = new ProfileForm();
            form.setRealName(user.getRealName());
            form.setEmail(user.getEmail());
            form.setPhone(user.getPhone());
            model.addAttribute("profileForm", form);
        }
        if (!model.containsAttribute("passwordForm")) {
            model.addAttribute("passwordForm", new PasswordForm());
        }
        model.addAttribute("user", user);
        return "profile";
    }

    /**
     * 保存个人资料。
     */
    @PostMapping
    public String updateProfile(@Valid @ModelAttribute("profileForm") ProfileForm form, BindingResult binding,
                                Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("user", sysUserService.getById(SecurityUtils.currentUserId()));
            model.addAttribute("passwordForm", new PasswordForm());
            return "profile";
        }
        try {
            sysUserService.updateProfile(form);
            redirect.addFlashAttribute("msg", "个人资料已保存");
            redirect.addFlashAttribute("msgType", "success");
        } catch (Exception e) {
            redirect.addFlashAttribute("msg", e.getMessage());
            redirect.addFlashAttribute("msgType", "danger");
        }
        return "redirect:/profile";
    }

    /**
     * 修改登录密码。
     */
    @PostMapping("/password")
    public String changePassword(@Valid @ModelAttribute("passwordForm") PasswordForm form, BindingResult binding,
                                 Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            String msg = binding.getFieldErrors().stream()
                    .map(e -> e.getDefaultMessage())
                    .findFirst().orElse("密码格式不正确");
            redirect.addFlashAttribute("msg", msg);
            redirect.addFlashAttribute("msgType", "danger");
            return "redirect:/profile";
        }
        try {
            sysUserService.changePassword(form);
            redirect.addFlashAttribute("msg", "密码修改成功，建议退出登录后使用新密码重新登录");
            redirect.addFlashAttribute("msgType", "success");
            return "redirect:/profile";
        } catch (Exception e) {
            redirect.addFlashAttribute("msg", e.getMessage());
            redirect.addFlashAttribute("msgType", "danger");
            return "redirect:/profile";
        }
    }
}
