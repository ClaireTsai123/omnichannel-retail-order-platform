package com.ordering.orderservice.config;

import com.ordering.common.kafka.KafkaTopics;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerErrorHandlerConfig {

    @Bean
    public DefaultErrorHandler defaultErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (record, e) -> {
                            if (record.topic().equals(KafkaTopics.PAYMENT_EVENTS_TOPIC)) {
                                return new TopicPartition(
                                        KafkaTopics.PAYMENT_EVENTS_DLQ_TOPIC,
                                        record.partition()
                                );
                            }
                            if (record.topic().equals(KafkaTopics.FULFILLMENT_EVENTS_TOPIC)) {
                                return new TopicPartition(
                                        KafkaTopics.FULFILLMENT_EVENTS_DLQ_TOPIC,
                                        record.partition()
                                );
                            }
                            return new TopicPartition(
                                    record.topic() + "-dlq",
                                    record.partition()
                            );
                        }
                );

        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    }
}
