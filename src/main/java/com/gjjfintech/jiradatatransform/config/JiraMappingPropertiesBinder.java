package com.gjjfintech.jiradatatransform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;

public class JiraMappingPropertiesBinder {

    /**
     * Loads the given YAML resource (e.g., "jira-mappings-source.yml") from the classpath
     * and binds it to a JiraMappingProperties instance.
     *
     * @param resourcePath the resource path; e.g., "classpath:jira-mappings-source.yml"
     * @return a JiraMappingProperties instance loaded from the YAML file
     */
    public static JiraMappingProperties bind(String resourcePath) {
        // Create a new ObjectMapper configured for YAML.
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        // Remove the "classpath:" prefix if present.
        String cleanPath = resourcePath.replace("classpath:", "");
        Resource resource = new ClassPathResource(cleanPath);
        try {
            return mapper.readValue(resource.getInputStream(), JiraMappingProperties.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to bind JiraMappingProperties from resource: " + resourcePath, e);
        }
    }
}
