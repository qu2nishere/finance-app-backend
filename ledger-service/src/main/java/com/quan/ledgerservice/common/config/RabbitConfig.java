package com.quan.ledgerservice.common.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange eventsExchange(@Value("${app.events.exchange}") String name) {
        return new TopicExchange(name, true, false);
    }
}
