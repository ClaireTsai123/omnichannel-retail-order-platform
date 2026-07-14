package com.ordering.fulfillmentservice.config;

import com.ordering.common.kafka.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {
    @Value("${app.kafka.topic.partitions:${KAFKA_TOPIC_PARTITIONS:6}}")
    private int partitions;

    @Value("${app.kafka.topic.replication-factor:${KAFKA_TOPIC_REPLICATION_FACTOR:1}}")
    private int replicationFactor;

    @Value("${app.kafka.topic.min-insync-replicas:${KAFKA_TOPIC_MIN_IN_SYNC_REPLICAS:1}}")
    private String minInSyncReplicas;

    @Bean
    public NewTopic fulfillmentEventsTopic() {
        return TopicBuilder.name(KafkaTopics.FULFILLMENT_EVENTS_TOPIC)
                .partitions(partitions)
                .replicas(replicationFactor)
                .config("min.insync.replicas", minInSyncReplicas)
                .build();
    }

    @Bean
    public NewTopic fulfillmentEventsDlqTopic() {
        return TopicBuilder.name(KafkaTopics.FULFILLMENT_EVENTS_DLQ_TOPIC)
                .partitions(partitions)
                .replicas(replicationFactor)
                .config("min.insync.replicas", minInSyncReplicas)
                .build();
    }

    @Bean
    public NewTopic fulfillmentOrderEventsDlqTopic() {
        return TopicBuilder.name(KafkaTopics.FULFILLMENT_ORDER_EVENTS_DLQ_TOPIC)
                .partitions(partitions)
                .replicas(replicationFactor)
                .config("min.insync.replicas", minInSyncReplicas)
                .build();
    }


}
