package com.fraudengine.core.config;

import com.fraudengine.core.exception.UnsupportedTransactionTypeException;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListenerConfigurer;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistrar;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class KafkaConsumerConfig implements KafkaListenerConfigurer {

    private final ApplicationProperties applicationProperties;
    private final LocalValidatorFactoryBean validator;

    @Override
    public void configureKafkaListeners(final KafkaListenerEndpointRegistrar registrar) {
        registrar.setValidator(validator);
    }

    @Bean
    public DefaultErrorHandler errorHandler(final DeadLetterPublishingRecoverer deadLetterPublishingRecoverer) {
        ApplicationProperties.KafkaSettings kafka = applicationProperties.getKafkaSettings();
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(kafka.getRetryAttempts());
        backOff.setInitialInterval(kafka.getRetryInitialIntervalMs());
        backOff.setMultiplier(kafka.getRetryBackoffMultiplier());

        DefaultErrorHandler handler = new DefaultErrorHandler(deadLetterPublishingRecoverer, backOff);
        handler.addNotRetryableExceptions(UnsupportedTransactionTypeException.class, DeserializationException.class);
        return handler;
    }

    // @KafkaListener picks up the factory by this bean name
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
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
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            final KafkaOperations<Object, Object> kafkaOperations, final ProducerFactory<Object, Object> producerFactory) {
        // records that failed deserialization are published as raw bytes
        Map<String, Object> bytesConfig = Map.of(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        KafkaTemplate<Object, Object> bytesTemplate = new KafkaTemplate<>(producerFactory, bytesConfig);

        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, bytesTemplate);
        templates.put(Object.class, kafkaOperations);

        return new DeadLetterPublishingRecoverer(
                templates,
                (record, ex) -> new TopicPartition(record.topic() + "-dlt", -1));
    }

}
