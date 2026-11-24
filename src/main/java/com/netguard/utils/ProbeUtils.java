package com.netguard.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * 网络连通性探测工具。
 *
 * <p>优先使用 TCP 端口探测（结果更贴近业务端口可用性，且不会被禁 ICMP 的策略误判）；
 * 未配置监控端口时退化为 ICMP 探测。</p>
 */
@Slf4j
public final class ProbeUtils {

    public static final String TYPE_TCP = "TCP";
    public static final String TYPE_ICMP = "ICMP";

    private ProbeUtils() {
    }

    /**
     * 探测设备连通性。
     *
     * @param ip        设备 IP
     * @param port      监控端口，为空或 &lt;=0 时使用 ICMP
     * @param timeoutMs 超时时间（毫秒）
     * @return 探测结果，包含成功标记、耗时与说明
     */
    public static ProbeResult probe(String ip, Integer port, int timeoutMs) {
        int timeout = timeoutMs <= 0 ? 2000 : timeoutMs;
        if (port != null && port > 0) {
            return probeTcp(ip, port, timeout);
        }
        return probeIcmp(ip, timeout);
    }

    /**
     * TCP 端口探测：能建立三次握手即视为在线。
     */
    private static ProbeResult probeTcp(String ip, int port, int timeout) {
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), timeout);
            long cost = System.currentTimeMillis() - start;
            return ProbeResult.of(true, TYPE_TCP, cost, "端口 " + port + " 连接成功");
        } catch (SocketTimeoutException e) {
            return ProbeResult.of(false, TYPE_TCP, System.currentTimeMillis() - start,
                    "端口 " + port + " 连接超时（" + timeout + "ms）");
        } catch (IOException e) {
            return ProbeResult.of(false, TYPE_TCP, System.currentTimeMillis() - start,
                    "端口 " + port + " 连接失败：" + e.getMessage());
        }
    }

    /**
     * ICMP 探测：调用 {@link InetAddress#isReachable(int)}。
     */
    private static ProbeResult probeIcmp(String ip, int timeout) {
        long start = System.currentTimeMillis();
        try {
            InetAddress address = InetAddress.getByName(ip);
            boolean reachable = address.isReachable(timeout);
            long cost = System.currentTimeMillis() - start;
            if (reachable) {
                return ProbeResult.of(true, TYPE_ICMP, cost, "ICMP 可达");
            }
            return ProbeResult.of(false, TYPE_ICMP, cost, "ICMP 不可达（可能禁 ping）");
        } catch (IOException e) {
            return ProbeResult.of(false, TYPE_ICMP, System.currentTimeMillis() - start,
                    "ICMP 探测异常：" + e.getMessage());
        }
    }
}
