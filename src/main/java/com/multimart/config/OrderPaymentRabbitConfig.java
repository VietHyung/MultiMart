package com.multimart.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Cấu hình RabbitMQ cho luồng hủy đơn hàng thường quá hạn thanh toán (TTL + Dead Letter Exchange):
 * tin nhắn nằm trong Delay Queue (không có consumer); khi hết TTL được chuyển sang Cancel Exchange/Queue.
 */
@Configuration
public class OrderPaymentRabbitConfig {

    public static final String EXCHANGE = "order.payment.exchange";
    public static final String DELAY_QUEUE = "order.payment.delay.queue";
    public static final String DELAY_ROUTING_KEY = "order.payment.delay";
    public static final String CANCEL_EXCHANGE = "order.payment.cancel.exchange";
    public static final String CANCEL_QUEUE = "order.payment.cancel.queue";
    public static final String CANCEL_ROUTING_KEY = "order.payment.cancel";

    /**
     * Exchange nhận tin nhắn hẹn giờ.
     *
     * @return DirectExchange bền vững
     */
    @Bean
    public DirectExchange orderPaymentExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    /**
     * Exchange nhận tin nhắn đã hết hạn (Dead Letter Exchange của Delay Queue).
     *
     * @return DirectExchange bền vững
     */
    @Bean
    public DirectExchange orderPaymentCancelExchange() {
        return new DirectExchange(CANCEL_EXCHANGE, true, false);
    }

    /**
     * Delay Queue giữ tin nhắn đến khi hết TTL rồi chuyển sang Cancel Exchange.
     *
     * @return Queue có cấu hình dead-letter
     */
    @Bean
    public Queue orderPaymentDelayQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", CANCEL_EXCHANGE);
        args.put("x-dead-letter-routing-key", CANCEL_ROUTING_KEY);
        return new Queue(DELAY_QUEUE, true, false, false, args);
    }

    /**
     * Hàng đợi nhận yêu cầu hủy đơn quá hạn.
     *
     * @return Queue bền vững
     */
    @Bean
    public Queue orderPaymentCancelQueue() {
        return new Queue(CANCEL_QUEUE, true);
    }

    /**
     * Ràng buộc Delay Queue với Exchange chính.
     *
     * @return Binding
     */
    @Bean
    public Binding orderPaymentDelayBinding() {
        return BindingBuilder.bind(orderPaymentDelayQueue()).to(orderPaymentExchange()).with(DELAY_ROUTING_KEY);
    }

    /**
     * Ràng buộc Cancel Queue với Cancel Exchange.
     *
     * @return Binding
     */
    @Bean
    public Binding orderPaymentCancelBinding() {
        return BindingBuilder.bind(orderPaymentCancelQueue()).to(orderPaymentCancelExchange()).with(CANCEL_ROUTING_KEY);
    }
}
