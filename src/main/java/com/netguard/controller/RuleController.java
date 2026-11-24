package com.netguard.controller;

import com.netguard.dto.RuleForm;
import com.netguard.entity.AlarmRule;
import com.netguard.service.AlarmRuleService;
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
 * 告警规则配置控制器（仅管理员可访问）。
 */
@Controller
@RequestMapping("/rule")
@RequiredArgsConstructor
public class RuleController {

    private final AlarmRuleService alarmRuleService;

    /**
     * 规则配置页。
     */
    @GetMapping
    public String form(Model model) {
        AlarmRule rule = alarmRuleService.getRule();
        RuleForm form = new RuleForm();
        form.setId(rule.getId());
        form.setRuleName(rule.getRuleName());
        form.setInspectInterval(rule.getInspectInterval());
        form.setFailThreshold(rule.getFailThreshold());
        form.setTimeoutMs(rule.getTimeoutMs());
        form.setUrgentEnabled(rule.getUrgentEnabled());
        form.setNotifyPageEnabled(rule.getNotifyPageEnabled());
        form.setNotifyMailEnabled(rule.getNotifyMailEnabled());
        form.setMailReceivers(rule.getMailReceivers());
        form.setActiveFlag(rule.getActiveFlag());
        form.setRemark(rule.getRemark());
        model.addAttribute("ruleForm", form);
        model.addAttribute("rule", rule);
        return "rule/form";
    }

    /**
     * 保存规则，保存后调度器立即按新间隔重排。
     */
    @PostMapping
    public String save(@Valid @ModelAttribute("ruleForm") RuleForm form, BindingResult binding,
                       Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("rule", alarmRuleService.getRule());
            return "rule/form";
        }
        try {
            alarmRuleService.save(form);
            redirect.addFlashAttribute("msg", "规则已保存，巡检任务已按新配置重新排程");
            redirect.addFlashAttribute("msgType", "success");
        } catch (Exception e) {
            redirect.addFlashAttribute("msg", e.getMessage());
            redirect.addFlashAttribute("msgType", "danger");
        }
        return "redirect:/rule";
    }
}
