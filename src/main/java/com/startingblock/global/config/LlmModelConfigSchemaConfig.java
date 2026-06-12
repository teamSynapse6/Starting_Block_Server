package com.startingblock.global.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

@Configuration
public class LlmModelConfigSchemaConfig {

    private static final String INITIALIZER_BEAN_NAME = "llmModelConfigSchemaInitializer";

    @Bean
    public static BeanFactoryPostProcessor entityManagerFactoryDependsOnLlmModelConfigSchema() {
        return beanFactory -> addDependsOn(beanFactory, "entityManagerFactory", INITIALIZER_BEAN_NAME);
    }

    @Bean(name = INITIALIZER_BEAN_NAME)
    public InitializingBean llmModelConfigSchemaInitializer(
            final JdbcTemplate jdbcTemplate,
            final Environment environment
    ) {
        return () -> {
            if (environment.acceptsProfiles(Profiles.of("openapi"))) {
                return;
            }

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS llm_model_config (
                        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        model_name VARCHAR(255) NOT NULL,
                        temperature FLOAT NULL,
                        top_p FLOAT NULL,
                        top_k INTEGER NULL,
                        max_output_tokens INTEGER NULL,
                        system_instruction TEXT NULL,
                        created_at DATETIME(6) NOT NULL,
                        updated_at DATETIME(6) NOT NULL,
                        UNIQUE KEY uk_llm_model_config_model_name (model_name)
                    )
                    """);

            jdbcTemplate.execute("""
                    ALTER TABLE `user`
                    MODIFY COLUMN email VARCHAR(255) NULL
                    """);

            jdbcTemplate.execute("""
                    ALTER TABLE `user`
                    MODIFY COLUMN provider ENUM('KAKAO', 'APPLE') NULL
                    """);
        };
    }

    private static void addDependsOn(
            final ConfigurableListableBeanFactory beanFactory,
            final String beanName,
            final String dependsOnBeanName
    ) {
        if (!beanFactory.containsBeanDefinition(beanName)) {
            return;
        }
        BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
        Set<String> dependsOn = new LinkedHashSet<>();
        if (beanDefinition.getDependsOn() != null) {
            dependsOn.addAll(Arrays.asList(beanDefinition.getDependsOn()));
        }
        dependsOn.add(dependsOnBeanName);
        beanDefinition.setDependsOn(dependsOn.toArray(String[]::new));
    }
}
