package com.fraudengine.core.config;

import com.fraudengine.core.exception.TransactionValidationException;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DeserializationException;

@Configuration
@RequiredArgsConstructor
public class KafkaConsumerConfig {

    private final ApplicationProperties applicationProperties;

    @Bean
    public DefaultErrorHandler errorHandler(final DeadLetterPublishingRecoverer deadLetterPublishingRecoverer) {
        ApplicationProperties.KafkaSettings kafka = applicationProperties.getKafkaSettings();
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(kafka.getRetryAttempts());
        backOff.setInitialInterval(kafka.getRetryInitialIntervalMs());
        backOff.setMultiplier(kafka.getRetryBackoffMultiplier());

        DefaultErrorHandler handler = new DefaultErrorHandler(deadLetterPublishingRecoverer, backOff);
        handler.addNotRetryableExceptions(TransactionValidationException.class, DeserializationException.class);
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> consumerKafkaListenerContainerFactory(
            final ConsumerFactory<String, Object> consumerFactory, final DefaultErrorHandler errorHandler) {
        final ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(errorHandler);
        factory.setConcurrency(applicationProperties.getKafkaSettings().getConsumerConcurrency());
        return factory;
    }

    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(final KafkaOperations<Object, Object> kafkaOperations) {
        return new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (record, ex) -> new TopicPartition(record.topic() + "-dlt", -1));
    }

}
