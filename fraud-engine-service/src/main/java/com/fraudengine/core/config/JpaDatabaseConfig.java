package com.fraudengine.core.config;

import jakarta.persistence.EntityManagerFactory;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableJpaRepositories(
        basePackages = "com.fraudengine.core.persistence.repository",
        entityManagerFactoryRef = "entityManagerFactory",
        transactionManagerRef = "transactionManager")
public class JpaDatabaseConfig {

    @Bean(name = "entityManagerFactory")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            @Qualifier("dataSource") final DataSource dataSource,
            final EntityManagerFactoryBuilder builder,
            final ApplicationProperties applicationProperties) {

        Map<String, Object> props = new HashMap<>();
        props.put(
                "hibernate.physical_naming_strategy",
                applicationProperties.getJpaSettings().getPhysicalNamingStrategy());
        props.put("hibernate.dialect", applicationProperties.getJpaSettings().getDialect());
        props.putAll(applicationProperties.getJpaSettings().getProperties());

        return builder.dataSource(dataSource)
                .packages("com.fraudengine.core.persistence.entity")
                .persistenceUnit("fraud-engine")
                .properties(props)
                .build();
    }

    @Bean("transactionManager")
    public PlatformTransactionManager transactionManager(
            @Qualifier("entityManagerFactory") final EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
