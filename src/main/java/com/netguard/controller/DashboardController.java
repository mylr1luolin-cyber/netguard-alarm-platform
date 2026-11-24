package com.netguard.controller;

import com.netguard.entity.AlarmRule;
import com.netguard.entity.InspectLog;
import com.netguard.service.AlarmRuleService;
import com.netguard.service.DashboardService;
import com.netguard.service.InspectService;
import com.netguard.service.NotifyService;
import com.netguard.vo.DashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 监控看板控制器：首页聚合数据与大屏图表。
 */
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private static final DateTimeFormatter FULL_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DashboardService dashboardService;
    private final AlarmRuleService alarmRuleService;
    private final InspectService inspectService;
    private final NotifyService notifyService;

    /**
     * 监控看板首页。
     */
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        DashboardVO summary = dashboardService.summary();
        AlarmRule rule = alarmRuleService.getRule();
        InspectLog latest = inspectService.latestLog();

        model.addAttribute("summary", summary);
        model.addAttribute("rule", rule);
        model.addAttribute("latestLog", latest);
        model.addAttribute("deviceTypes", dashboardService.deviceTypeDistribution());
        model.addAttribute("trend", dashboardService.monthlyTrend(30));
        model.addAttribute("levels", dashboardService.levelDistribution());
        model.addAttribute("topFaults", dashboardService.topFaultDevices(5));
        model.addAttribute("unreadCount", notifyService.unreadCount());
        model.addAttribute("serverTime", LocalDateTime.now().format(FULL_FORMAT));
        return "dashboard";
    }
}
