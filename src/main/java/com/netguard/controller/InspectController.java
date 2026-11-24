package com.netguard.controller;

import com.netguard.entity.InspectLog;
import com.netguard.service.InspectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 巡检记录控制器：批次列表、批次明细与手动触发巡检。
 */
@Controller
@RequestMapping("/inspects")
@RequiredArgsConstructor
public class InspectController {

    private final InspectService inspectService;

    /**
     * 巡检批次列表。
     */
    @GetMapping
    public String list(@RequestParam(value = "page", defaultValue = "1") int page,
                       @RequestParam(value = "size", defaultValue = "10") int size,
                       Model model) {
        model.addAttribute("page", inspectService.pageLogs(page, size));
        model.addAttribute("latestLog", inspectService.latestLog());
        model.addAttribute("offlineDevices", inspectService.listOfflineDevices());
        return "inspect/list";
    }

    /**
     * 手动立即巡检（管理员）。
     */
    @PostMapping("/run")
    public String run(RedirectAttributes redirect) {
        try {
            InspectLog log = inspectService.runManual();
            redirect.addFlashAttribute("msg", String.format("巡检完成：共 %d 台，在线 %d 台，离线 %d 台，耗时 %d ms",
                    log.getTotalCount(), log.getOnlineCount(), log.getOfflineCount(), log.getCostMs()));
            redirect.addFlashAttribute("msgType", "success");
            return "redirect:/inspects/" + log.getId();
        } catch (Exception e) {
            redirect.addFlashAttribute("msg", "巡检执行失败：" + e.getMessage());
            redirect.addFlashAttribute("msgType", "danger");
            return "redirect:/inspects";
        }
    }

    /**
     * 批次明细。
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("log", inspectService.getLog(id));
        model.addAttribute("details", inspectService.listDetails(id));
        return "inspect/detail";
    }
}
