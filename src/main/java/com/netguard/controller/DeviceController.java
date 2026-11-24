package com.netguard.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.netguard.dto.DeviceForm;
import com.netguard.entity.Device;
import com.netguard.entity.DeviceType;
import com.netguard.query.DeviceQuery;
import com.netguard.service.DeviceService;
import com.netguard.service.DeviceTypeService;
import com.netguard.service.InspectService;
import com.netguard.utils.ProbeResult;
import com.netguard.vo.DeviceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;
import java.util.List;

/**
 * 设备管理控制器：列表、新增、编辑、删除与单台设备即时探测。
 */
@Controller
@RequestMapping("/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;
    private final DeviceTypeService deviceTypeService;
    private final InspectService inspectService;

    /**
     * 设备列表，支持关键字、类型、状态过滤与排序。
     */
    @GetMapping
    public String list(@ModelAttribute("query") DeviceQuery query, Model model) {
        IPage<DeviceVO> page = deviceService.page(query);
        model.addAttribute("page", page);
        model.addAttribute("types", deviceTypeService.listAll());
        return "device/list";
    }

    /**
     * 新增设备表单。
     */
    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("deviceForm", new DeviceForm());
        model.addAttribute("types", deviceTypeService.listAll());
        model.addAttribute("editing", false);
        return "device/form";
    }

    /**
     * 提交新增。
     */
    @PostMapping
    public String create(@Valid @ModelAttribute("deviceForm") DeviceForm form, BindingResult binding,
                         @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                         Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return redisplayForm(form, model, false);
        }
        try {
            deviceService.create(form, imageFile);
            redirect.addFlashAttribute("msg", "设备「" + form.getDeviceName() + "」已加入监控");
            redirect.addFlashAttribute("msgType", "success");
            return "redirect:/devices";
        } catch (Exception e) {
            model.addAttribute("errorMsg", e.getMessage());
            return redisplayForm(form, model, false);
        }
    }

    /**
     * 编辑设备表单。
     */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Device device = deviceService.getById(id);
        DeviceForm form = new DeviceForm();
        form.setId(device.getId());
        form.setDeviceCode(device.getDeviceCode());
        form.setDeviceName(device.getDeviceName());
        form.setIpAddress(device.getIpAddress());
        form.setDeviceTypeId(device.getDeviceTypeId());
        form.setMonitorPort(device.getMonitorPort());
        form.setLocation(device.getLocation());
        form.setCoreFlag(device.getCoreFlag());
        form.setRemark(device.getRemark());
        form.setImageUrl(device.getImageUrl());
        model.addAttribute("deviceForm", form);
        model.addAttribute("types", deviceTypeService.listAll());
        model.addAttribute("device", device);
        model.addAttribute("editing", true);
        return "device/form";
    }

    /**
     * 提交编辑。
     */
    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("deviceForm") DeviceForm form,
                         BindingResult binding,
                         @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                         Model model, RedirectAttributes redirect) {
        form.setId(id);
        if (binding.hasErrors()) {
            return redisplayForm(form, model, true);
        }
        try {
            deviceService.update(form, imageFile);
            redirect.addFlashAttribute("msg", "设备「" + form.getDeviceName() + "」已更新");
            redirect.addFlashAttribute("msgType", "success");
            return "redirect:/devices";
        } catch (Exception e) {
            model.addAttribute("errorMsg", e.getMessage());
            return redisplayForm(form, model, true);
        }
    }

    /**
     * 删除设备。
     */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            deviceService.delete(id);
            redirect.addFlashAttribute("msg", "设备已删除");
            redirect.addFlashAttribute("msgType", "success");
        } catch (Exception e) {
            redirect.addFlashAttribute("msg", e.getMessage());
            redirect.addFlashAttribute("msgType", "danger");
        }
        return "redirect:/devices";
    }

    /**
     * 单台设备立即探测，便于新增后立刻验证连通性。
     */
    @PostMapping("/{id}/check")
    public String check(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            ProbeResult result = inspectService.checkOne(id);
            redirect.addFlashAttribute("msg", "探测完成：" + result.getMessage());
            redirect.addFlashAttribute("msgType", result.isSuccess() ? "success" : "warning");
        } catch (Exception e) {
            redirect.addFlashAttribute("msg", "探测失败：" + e.getMessage());
            redirect.addFlashAttribute("msgType", "danger");
        }
        return "redirect:/devices";
    }

    private String redisplayForm(DeviceForm form, Model model, boolean editing) {
        List<DeviceType> types = deviceTypeService.listAll();
        model.addAttribute("types", types);
        model.addAttribute("editing", editing);
        return "device/form";
    }
}
