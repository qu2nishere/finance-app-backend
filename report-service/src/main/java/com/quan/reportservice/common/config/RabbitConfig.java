package com.quan.reportservice.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String ALL_EVENTS_QUEUE = "report.all-events";
    public static final String ALL_EVENTS_DLQ = "report.all-events.dlq";

    @Bean
    public TopicExchange eventsExchange(@Value("${app.events.exchange}") String name) {
        return new TopicExchange(name, true, false);
    }

    @Bean
    public Queue reportAllEventsQueue() {
        return QueueBuilder.durable(ALL_EVENTS_QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(ALL_EVENTS_DLQ)
                .build();
    }

    @Bean
    public Queue reportAllEventsDlq() {
        return QueueBuilder.durable(ALL_EVENTS_DLQ).build();
    }

    @Bean
    public Binding reportAllEventsBinding(Queue reportAllEventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(reportAllEventsQueue).to(eventsExchange).with("#");
    }
}
