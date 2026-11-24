package com.netguard.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 告警规则变更事件。
 *
 * <p>规则保存后发布该事件，由动态巡检调度器监听并重新排程，
 * 避免 Service 层直接依赖调度组件造成循环依赖。</p>
 */
@Getter
public class RuleChangedEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /** 最新的巡检间隔（分钟） */
    private final int inspectInterval;

    /** 是否启用自动巡检 */
    private final boolean active;

    public RuleChangedEvent(Object source, int inspectInterval, boolean active) {
        super(source);
        this.inspectInterval = inspectInterval;
        this.active = active;
    }
}
