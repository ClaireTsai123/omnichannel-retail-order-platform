package com.ordering.fulfillmentservice.config;

import com.ordering.common.kafka.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic fulfillmentEventsDlqTopic() {
        return TopicBuilder.name(KafkaTopics.FULFILLMENT_EVENTS_DLQ_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic fulfillmentOrderEventsDlqTopic() {
        return TopicBuilder.name(KafkaTopics.FULFILLMENT_ORDER_EVENTS_DLQ_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }


}
