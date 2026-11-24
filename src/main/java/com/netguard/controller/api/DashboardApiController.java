package com.netguard.controller.api;

import com.netguard.common.Result;
import com.netguard.service.DashboardService;
import com.netguard.service.NotifyService;
import com.netguard.vo.CountItemVO;
import com.netguard.vo.DashboardVO;
import com.netguard.vo.DeviceFaultRankVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 看板数据接口：供页面定时轮询刷新与 ECharts 动态渲染。
 *
 * <p>虽然整体采用 Thymeleaf 服务端渲染，但监控大屏需要无刷新更新数据，
 * 这里以 AJAX 调用少量 JSON 接口，不违反“禁止前后端分离”的约束。</p>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardApiController {

    private final DashboardService dashboardService;
    private final NotifyService notifyService;

    /**
     * 看板汇总数据。
     */
    @GetMapping("/dashboard/summary")
    public Result<DashboardVO> summary() {
        return Result.ok(dashboardService.summary());
    }

    /**
     * 图表数据：设备类型分布、在线状态分布、30 天趋势、级别分布、故障排行。
     */
    @GetMapping("/dashboard/charts")
    public Result<Map<String, Object>> charts() {
        Map<String, Object> data = new HashMap<>(6);
        List<CountItemVO> types = dashboardService.deviceTypeDistribution();
        List<CountItemVO> status = dashboardService.statusDistribution();
        List<CountItemVO> trend = dashboardService.monthlyTrend(30);
        List<CountItemVO> levels = dashboardService.levelDistribution();
        List<DeviceFaultRankVO> ranks = dashboardService.topFaultDevices(5);
        data.put("deviceTypes", types);
        data.put("status", status);
        data.put("trend", trend);
        data.put("levels", levels);
        data.put("faultRanks", ranks);
        return Result.ok(data);
    }

    /**
     * 未读消息数量。
     */
    @GetMapping("/notices/unread")
    public Result<Long> unread() {
        return Result.ok(notifyService.unreadCount());
    }
}
