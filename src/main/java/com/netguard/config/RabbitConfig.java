package com.netguard.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netguard.utils.JsonUtils;

/**
 * RabbitMQ 配置：告警通知直连交换机 + 死信队列。
 *
 * <p>交换机 netguard.alarm.exchange 绑定队列 netguard.alarm.queue；
 * 队列处理失败的消息进入死信交换机 netguard.alarm.dlx，最终落到死信队列，
 * 便于人工排查，避免告警消息静默丢失。</p>
 *
 * <p>当 app.mq.enabled=false 时本配置整体不生效，系统改用本地同步通知。</p>
 */
@Configuration
@ConditionalOnProperty(name = "app.mq.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitConfig {

    /** 告警业务交换机 */
    public static final String ALARM_EXCHANGE = "netguard.alarm.exchange";
    /** 告警业务队列 */
    public static final String ALARM_QUEUE = "netguard.alarm.queue";
    /** 告警路由键 */
    public static final String ALARM_ROUTING_KEY = "netguard.alarm.notify";

    /** 死信交换机 */
    public static final String DEAD_LETTER_EXCHANGE = "netguard.alarm.dlx";
    /** 死信队列 */
    public static final String DEAD_LETTER_QUEUE = "netguard.alarm.dlq";
    /** 死信路由键 */
    public static final String DEAD_LETTER_ROUTING_KEY = "netguard.alarm.dead";

    @Bean
    public DirectExchange alarmExchange() {
        return new DirectExchange(ALARM_EXCHANGE, true, false);
    }

    @Bean
    public Queue alarmQueue() {
        return QueueBuilder.durable(ALARM_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding alarmBinding() {
        return BindingBuilder.bind(alarmQueue()).to(alarmExchange()).with(ALARM_ROUTING_KEY);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(DEAD_LETTER_ROUTING_KEY);
    }

    /**
     * JSON 消息转换器，统一时间格式，方便在 RabbitMQ 控制台直接查看消息内容。
     */
    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter(JsonUtils.getMapper());
    }

    /**
     * 自定义 RabbitTemplate：指定转换器，并开启消息发送失败返回回调日志。
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jacksonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonMessageConverter);
        template.setMandatory(true);
        template.setReturnsCallback(returned ->
                org.slf4j.LoggerFactory.getLogger(RabbitConfig.class)
                        .error("告警消息未能路由到队列：replyCode={}, replyText={}",
                                returned.getReplyCode(), returned.getReplyText()));
        return template;
    }
}
