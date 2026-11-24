/**
 * 运行总览看板：加载图表数据并渲染 ECharts
 * 数据来源：GET /api/dashboard/charts
 */
(function () {
    'use strict';

    var charts = {};

    var palette = ['#2563eb', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#0ea5e9', '#64748b'];

    /** 初始化或复用 ECharts 实例 */
    function use(id) {
        if (!window.echarts) {
            return null;
        }
        var dom = document.getElementById(id);
        if (!dom) {
            return null;
        }
        if (!charts[id]) {
            charts[id] = window.echarts.init(dom);
        }
        return charts[id];
    }

    function optionBase() {
        return {
            textStyle: { fontFamily: 'Microsoft YaHei, PingFang SC, sans-serif' },
            tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
            grid: { left: 12, right: 18, top: 30, bottom: 8, containLabel: true }
        };
    }

    /** 近 30 天告警趋势（面积折线） */
    function renderTrend(list) {
        var chart = use('trendChart');
        if (!chart) {
            return;
        }
        var names = [], values = [];
        (list || []).forEach(function (i) {
            names.push(i.name);
            values.push(i.value);
        });
        var opt = optionBase();
        opt.tooltip = { trigger: 'axis' };
        opt.xAxis = {
            type: 'category',
            boundaryGap: false,
            data: names,
            axisLine: { lineStyle: { color: '#e2e8f0' } },
            axisLabel: { color: '#64748b', fontSize: 11 }
        };
        opt.yAxis = {
            type: 'value',
            minInterval: 1,
            splitLine: { lineStyle: { color: '#eef2f7' } },
            axisLabel: { color: '#64748b', fontSize: 11 }
        };
        opt.series = [{
            name: '告警次数',
            type: 'line',
            smooth: true,
            symbol: 'circle',
            symbolSize: 6,
            data: values,
            lineStyle: { width: 3, color: '#2563eb' },
            itemStyle: { color: '#2563eb' },
            areaStyle: {
                color: {
                    type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
                    colorStops: [
                        { offset: 0, color: 'rgba(37,99,235,.32)' },
                        { offset: 1, color: 'rgba(37,99,235,.02)' }
                    ]
                }
            }
        }];
        chart.setOption(opt, true);
    }

    /** 环形图通用渲染 */
    function renderDonut(id, list, centerText) {
        var chart = use(id);
        if (!chart) {
            return;
        }
        var data = (list || []).map(function (i) {
            return { name: i.name, value: i.value };
        });
        chart.setOption({
            textStyle: { fontFamily: 'Microsoft YaHei, sans-serif' },
            color: palette,
            tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
            legend: {
                orient: 'vertical',
                right: 8,
                top: 'center',
                itemWidth: 9,
                itemHeight: 9,
                textStyle: { color: '#475569', fontSize: 12 }
            },
            graphic: centerText ? [{
                type: 'text',
                left: '30%',
                top: 'center',
                style: {
                    text: centerText,
                    textAlign: 'center',
                    fill: '#1e293b',
                    fontSize: 18,
                    fontWeight: 'bold'
                }
            }] : [],
            series: [{
                type: 'pie',
                radius: ['46%', '68%'],
                center: ['32%', '50%'],
                avoidLabelOverlap: true,
                itemStyle: { borderColor: '#fff', borderWidth: 2 },
                label: { show: false },
                labelLine: { show: false },
                data: data
            }]
        }, true);
    }

    /** 级别分布柱状图 */
    function renderLevel(list) {
        var chart = use('levelChart');
        if (!chart) {
            return;
        }
        var names = [], values = [], colors = [];
        var colorMap = { '紧急': '#ef4444', '普通': '#0ea5e9', 'URGENT': '#ef4444', 'NORMAL': '#0ea5e9' };
        (list || []).forEach(function (i) {
            names.push(i.name);
            values.push(i.value);
            colors.push(colorMap[i.name] || '#2563eb');
        });
        var opt = optionBase();
        opt.tooltip = { trigger: 'axis' };
        opt.xAxis = {
            type: 'category',
            data: names,
            axisLine: { lineStyle: { color: '#e2e8f0' } },
            axisLabel: { color: '#64748b', fontSize: 12 }
        };
        opt.yAxis = {
            type: 'value',
            minInterval: 1,
            splitLine: { lineStyle: { color: '#eef2f7' } },
            axisLabel: { color: '#64748b', fontSize: 11 }
        };
        opt.series = [{
            type: 'bar',
            barWidth: '42%',
            data: values.map(function (v, idx) {
                return {
                    value: v,
                    itemStyle: {
                        color: colors[idx],
                        borderRadius: [8, 8, 0, 0]
                    }
                };
            }),
            label: { show: true, position: 'top', color: '#475569', fontSize: 12 }
        }];
        chart.setOption(opt, true);
    }

    function load() {
        if (!window.fetch) {
            return;
        }
        fetch('/api/dashboard/charts', { headers: { 'X-Requested-With': 'XMLHttpRequest' } })
            .then(function (r) { return r.json(); })
            .then(function (res) {
                var d = (res && res.data) || {};
                renderTrend(d.trend);
                renderDonut('statusChart', d.status, null);
                renderDonut('typeChart', d.deviceTypes, null);
                renderLevel(d.levels);
            })
            .catch(function (e) {
                if (window.console) {
                    console.warn('看板图表数据加载失败', e);
                }
            });
    }

    function resizeAll() {
        Object.keys(charts).forEach(function (k) {
            charts[k].resize();
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        load();
        window.addEventListener('resize', resizeAll);
        // 每 60 秒自动刷新一次看板数据
        setInterval(load, 60000);
    });
})();
