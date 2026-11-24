package com.netguard.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.netguard.dto.AlarmConfirmForm;
import com.netguard.query.AlarmQuery;
import com.netguard.service.AlarmService;
import com.netguard.service.DashboardService;
import com.netguard.vo.AlarmRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 告警控制器：告警列表、详情、确认处理与历史统计。
 */
@Controller
@RequestMapping("/alarms")
@RequiredArgsConstructor
public class AlarmController {

    private final AlarmService alarmService;
    private final DashboardService dashboardService;

    /**
     * 告警列表，支持时间范围、级别、状态、关键字过滤。
     */
    @GetMapping
    public String list(@ModelAttribute("query") AlarmQuery query, Model model) {
        IPage<AlarmRecordVO> page = alarmService.page(query);
        model.addAttribute("page", page);
        model.addAttribute("pendingCount", alarmService.pendingCount());
        return "alarm/list";
    }

    /**
     * 告警详情页。
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("alarm", alarmService.detail(id));
        model.addAttribute("confirmForm", new AlarmConfirmForm());
        return "alarm/detail";
    }

    /**
     * 确认告警并记录处理情况。
     */
    @PostMapping("/confirm")
    public String confirm(@Valid @ModelAttribute("confirmForm") AlarmConfirmForm form, BindingResult binding,
                          RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("msg", "处理说明过长，请精简后再提交");
            redirect.addFlashAttribute("msgType", "danger");
            return "redirect:/alarms/" + form.getId();
        }
        try {
            alarmService.confirm(form);
            redirect.addFlashAttribute("msg", "告警已确认，处理情况已记录");
            redirect.addFlashAttribute("msgType", "success");
        } catch (Exception e) {
            redirect.addFlashAttribute("msg", e.getMessage());
            redirect.addFlashAttribute("msgType", "danger");
        }
        return "redirect:/alarms/" + form.getId();
    }

    /**
     * 告警历史统计：时间范围查询、设备故障排行、月度趋势与级别分布。
     */
    @GetMapping("/statistics")
    public String statistics(
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Model model) {
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        LocalDate start = startDate == null ? end.withDayOfMonth(1) : startDate;
        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }
        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = end.plusDays(1).atStartOfDay();

        // 按天统计只有"有告警"的日期才返回记录，这里补齐区间内缺失日期，保证折线连续
        List<com.netguard.vo.CountItemVO> daily = fillDaily(start, end, alarmService.countByDay(startTime, endTime));
        model.addAttribute("startDate", start);
        model.addAttribute("endDate", end);
        model.addAttribute("dailyTrend", daily);
        model.addAttribute("levelStats", alarmService.countByLevel(startTime, endTime));
        model.addAttribute("faultRanks", alarmService.faultRank(startTime, endTime, 10));
        model.addAttribute("totalAlarms", alarmService.countOffline(startTime, endTime));
        model.addAttribute("avgDuration", alarmService.avgDurationMinutes(startTime, endTime));
        model.addAttribute("pendingCount", alarmService.pendingCount());
        model.addAttribute("monthTrend", dashboardService.monthlyTrend(30));
        return "alarm/statistics";
    }

    /**
     * 把稀疏的按天统计结果补齐为连续日期序列。
     *
     * @param start 起始日期（含）
     * @param end   结束日期（含）
     * @param raw   SQL 聚合结果，name 为 yyyy-MM-dd
     * @return 每天一条记录，无数据填 0；区间超过 120 天时最长只展示 120 天
     */
    private List<com.netguard.vo.CountItemVO> fillDaily(LocalDate start, LocalDate end,
                                                        List<com.netguard.vo.CountItemVO> raw) {
        Map<String, Integer> counted = new HashMap<>();
        if (raw != null) {
            raw.forEach(item -> counted.put(String.valueOf(item.getName()), item.getValue()));
        }
        LocalDate from = end.minusDays(119).isAfter(start) ? end.minusDays(119) : start;
        List<com.netguard.vo.CountItemVO> result = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(end); day = day.plusDays(1)) {
            String label = day.format(DateTimeFormatter.ofPattern("MM-dd"));
            result.add(new com.netguard.vo.CountItemVO(label, counted.getOrDefault(day.toString(), 0), null));
        }
        return result;
    }
}
