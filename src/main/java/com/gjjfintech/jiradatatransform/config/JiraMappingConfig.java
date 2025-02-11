package com.gjjfintech.jiradatatransform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JiraMappingConfig {

    @Bean("sourceMappingProperties")
    public JiraMappingProperties sourceMappingProperties(
            @Value("${jira.source.mapping-config}") String mappingConfigPath) {
        return JiraMappingPropertiesBinder.bind(mappingConfigPath);
    }

    @Bean("destinationMappingProperties")
    public JiraMappingProperties destinationMappingProperties(
            @Value("${jira.destination.mapping-config}") String mappingConfigPath) {
        return JiraMappingPropertiesBinder.bind(mappingConfigPath);
    }

}
