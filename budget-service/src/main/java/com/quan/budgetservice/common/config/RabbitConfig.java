package com.quan.budgetservice.common.config;

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

    public static final String TXN_EVENTS_QUEUE = "budget.txn-events";
    public static final String TXN_EVENTS_DLQ = "budget.txn-events.dlq";

    @Bean
    public TopicExchange eventsExchange(@Value("${app.events.exchange}") String name) {
        return new TopicExchange(name, true, false);
    }

    @Bean
    public Queue budgetTxnEventsQueue() {
        return QueueBuilder.durable(TXN_EVENTS_QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(TXN_EVENTS_DLQ)
                .build();
    }

    @Bean
    public Queue budgetTxnEventsDlq() {
        return QueueBuilder.durable(TXN_EVENTS_DLQ).build();
    }

    @Bean
    public Binding budgetTxnEventsBinding(Queue budgetTxnEventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(budgetTxnEventsQueue).to(eventsExchange).with("transaction.*");
    }
}
