package com.gjjfintech.jiradatatransform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JiraMappingConfig {

    @Bean(name = "sourceMappingProperties")
    public JiraMappingProperties sourceMappingProperties() {
        return JiraMappingPropertiesBinder.bind("classpath:jira-mappings-source.yml");
    }

    @Bean(name = "destinationMappingProperties")
    public JiraMappingProperties destinationMappingProperties() {
        return JiraMappingPropertiesBinder.bind("classpath:jira-mappings-destination.yml");
    }
}
