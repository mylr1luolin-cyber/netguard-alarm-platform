/**
 * NetGuard 前端通用脚本
 * 负责：顶部时钟、闪存消息自动消失、危险操作二次确认、未读消息角标轮询
 */
(function () {
    'use strict';

    /** 顶部时钟：每秒刷新，显示到秒 */
    function initClock() {
        var el = document.getElementById('topClock');
        if (!el) {
            return;
        }
        var week = ['日', '一', '二', '三', '四', '五', '六'];
        function tick() {
            var d = new Date();
            var pad = function (n) { return n < 10 ? '0' + n : '' + n; };
            el.textContent = d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
                + ' 星期' + week[d.getDay()] + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
        }
        tick();
        setInterval(tick, 1000);
    }

    /** 闪存消息 4 秒后淡出 */
    function initFlash() {
        var wrap = document.querySelector('.flash-wrap');
        if (!wrap) {
            return;
        }
        setTimeout(function () {
            Array.prototype.forEach.call(wrap.children, function (item) {
                item.style.transition = 'opacity .4s ease, transform .4s ease';
                item.style.opacity = '0';
                item.style.transform = 'translateX(20px)';
            });
            setTimeout(function () { wrap.style.display = 'none'; }, 450);
        }, 4000);
    }

    /** 带 data-confirm 的表单提交前确认 */
    function initConfirm() {
        document.addEventListener('submit', function (e) {
            var msg = e.target.getAttribute && e.target.getAttribute('data-confirm');
            if (msg && !window.confirm(msg)) {
                e.preventDefault();
            }
        });
    }

    /** 未读消息数轮询（30 秒一次） */
    function initUnreadPoll() {
        var dot = document.querySelector('.icon-btn .dot');
        if (!dot || !window.fetch) {
            return;
        }
        setInterval(function () {
            fetch('/api/notices/unread', { headers: { 'X-Requested-With': 'XMLHttpRequest' } })
                .then(function (r) { return r.json(); })
                .then(function (res) {
                    var n = res && res.data ? res.data : 0;
                    if (n > 0) {
                        dot.textContent = n > 99 ? '99+' : n;
                        dot.style.display = '';
                    } else {
                        dot.style.display = 'none';
                    }
                })
                .catch(function () { /* 忽略轮询失败 */ });
        }, 30000);
    }

    /** 表格行点击跳转（data-href） */
    function initRowLink() {
        document.addEventListener('click', function (e) {
            var row = e.target.closest ? e.target.closest('[data-href]') : null;
            if (row && !e.target.closest('a') && !e.target.closest('button') && !e.target.closest('form')) {
                window.location.href = row.getAttribute('data-href');
            }
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        initClock();
        initFlash();
        initConfirm();
        initUnreadPoll();
        initRowLink();
    });
})();
