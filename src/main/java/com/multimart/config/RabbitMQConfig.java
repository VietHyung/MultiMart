package com.multimart.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Cấu hình hạ tầng hàng đợi tin nhắn RabbitMQ cho hệ thống MultiMart.
 * Định nghĩa Exchange, Main Queue, Dead Letter Exchange (DLX) và Dead Letter Queue (DLQ).
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "flashsale.order.exchange";
    public static final String ORDER_QUEUE = "flashsale.order.queue";
    public static final String ORDER_ROUTING_KEY = "flashsale.order.create";

    public static final String DLX_NAME = "flashsale.order.dlx";
    public static final String DLQ_NAME = "flashsale.order.dlq";
    public static final String DLQ_ROUTING_KEY = "flashsale.order.dlq";

    /**
     * Khởi tạo Direct Exchange chính xử lý đơn hàng Flash Sale.
     *
     * @return DirectExchange
     */
    @Bean
    public DirectExchange flashSaleOrderExchange() {
        return new DirectExchange(EXCHANGE_NAME, true, false);
    }

    /**
     * Khởi tạo Dead Letter Exchange (DLX) tiếp nhận các tin nhắn bị lỗi sau nhiều lần retry.
     *
     * @return DirectExchange cho DLX
     */
    @Bean
    public DirectExchange flashSaleOrderDlx() {
        return new DirectExchange(DLX_NAME, true, false);
    }

    /**
     * Khởi tạo hàng đợi chính xử lý đơn hàng Flash Sale.
     * Cấu hình chuyển tiếp sang DLX khi xảy ra lỗi hoặc từ chối tin nhắn.
     *
     * @return Queue với cấu hình x-dead-letter-exchange
     */
    @Bean
    public Queue flashSaleOrderQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_NAME);
        args.put("x-dead-letter-routing-key", DLQ_ROUTING_KEY);
        return new Queue(ORDER_QUEUE, true, false, false, args);
    }

    /**
     * Khởi tạo Dead Letter Queue (DLQ) để lưu vết các tin nhắn lỗi phục vụ điều tra/khắc phục.
     *
     * @return Queue DLQ
     */
    @Bean
    public Queue flashSaleOrderDlq() {
        return new Queue(DLQ_NAME, true, false, false);
    }

    /**
     * Ràng buộc hàng đợi chính với Exchange chính theo routing key.
     *
     * @return Binding
     */
    @Bean
    public Binding bindingOrderQueue() {
        return BindingBuilder.bind(flashSaleOrderQueue())
                .to(flashSaleOrderExchange())
                .with(ORDER_ROUTING_KEY);
    }

    /**
     * Ràng buộc hàng đợi Dead Letter với Dead Letter Exchange.
     *
     * @return Binding DLQ
     */
    @Bean
    public Binding bindingDlq() {
        return BindingBuilder.bind(flashSaleOrderDlq())
                .to(flashSaleOrderDlx())
                .with(DLQ_ROUTING_KEY);
    }

    /**
     * Khởi tạo ObjectMapper toàn cục hỗ trợ Java 8 Date/Time (JavaTimeModule).
     *
     * @return ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        return mapper;
    }

    /**
     * Bộ chuyển đổi tin nhắn Message sang định dạng JSON sử dụng Jackson ObjectMapper.
     *
     * @param objectMapper ObjectMapper chung của Spring
     * @return Jackson2JsonMessageConverter
     */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * Khởi tạo RabbitTemplate sử dụng bộ chuyển đổi JSON.
     *
     * @param connectionFactory ConnectionFactory của RabbitMQ
     * @param messageConverter  Bộ chuyển đổi JSON
     * @return RabbitTemplate
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
