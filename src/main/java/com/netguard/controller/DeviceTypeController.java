package com.netguard.controller;

import com.netguard.dto.DeviceTypeForm;
import com.netguard.service.DeviceTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;

/**
 * 设备类型字典控制器（仅管理员可维护）。
 */
@Controller
@RequestMapping("/types")
@RequiredArgsConstructor
public class DeviceTypeController {

    private final DeviceTypeService deviceTypeService;

    /**
     * 类型列表与新增表单。
     */
    @GetMapping
    public String list(Model model) {
        if (!model.containsAttribute("typeForm")) {
            model.addAttribute("typeForm", new DeviceTypeForm());
        }
        model.addAttribute("types", deviceTypeService.listAll());
        return "type/list";
    }

    /**
     * 新增类型。
     */
    @PostMapping
    public String create(@Valid @ModelAttribute("typeForm") DeviceTypeForm form, BindingResult binding,
                         Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("types", deviceTypeService.listAll());
            return "type/list";
        }
        try {
            deviceTypeService.create(form);
            redirect.addFlashAttribute("msg", "类型「" + form.getTypeName() + "」已新增");
            redirect.addFlashAttribute("msgType", "success");
        } catch (Exception e) {
            redirect.addFlashAttribute("msg", e.getMessage());
            redirect.addFlashAttribute("msgType", "danger");
        }
        return "redirect:/types";
    }

    /**
     * 修改类型。
     */
    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("typeForm") DeviceTypeForm form,
                         BindingResult binding, Model model, RedirectAttributes redirect) {
        form.setId(id);
        if (binding.hasErrors()) {
            model.addAttribute("types", deviceTypeService.listAll());
            return "type/list";
        }
        try {
            deviceTypeService.update(form);
            redirect.addFlashAttribute("msg", "类型已更新");
            redirect.addFlashAttribute("msgType", "success");
        } catch (Exception e) {
            redirect.addFlashAttribute("msg", e.getMessage());
            redirect.addFlashAttribute("msgType", "danger");
        }
        return "redirect:/types";
    }

    /**
     * 删除类型，存在设备时会被拒绝。
     */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            deviceTypeService.delete(id);
            redirect.addFlashAttribute("msg", "类型已删除");
            redirect.addFlashAttribute("msgType", "success");
        } catch (Exception e) {
            redirect.addFlashAttribute("msg", e.getMessage());
            redirect.addFlashAttribute("msgType", "danger");
        }
        return "redirect:/types";
    }
}
