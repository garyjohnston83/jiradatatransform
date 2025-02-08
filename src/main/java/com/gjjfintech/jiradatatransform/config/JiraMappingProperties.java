package com.gjjfintech.jiradatatransform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties
@PropertySource(factory = YamlPropertySourceFactory.class, value = "classpath:jira-mapping.yml")
public class JiraMappingProperties {

    /**
     * This map holds the field mappings keyed by the human-readable field name.
     * For example, "Issue Key", "Summary", etc.
     */
    private Map<String, FieldMapping> jiraFieldMappings;

    public Map<String, FieldMapping> getJiraFieldMappings() {
        return jiraFieldMappings;
    }

    public void setJiraFieldMappings(Map<String, FieldMapping> jiraFieldMappings) {
        this.jiraFieldMappings = jiraFieldMappings;
    }

    /**
     * Represents the configuration for a single field mapping.
     * Depending on the field, some properties may be null.
     */
    public static class FieldMapping {
        private String issueAttributeName;
        private String dataType;
        private Boolean isParentLink;
        private IssueLinkMapping issueLink;

        public String getIssueAttributeName() {
            return issueAttributeName;
        }

        public void setIssueAttributeName(String issueAttributeName) {
            this.issueAttributeName = issueAttributeName;
        }

        public String getDataType() {
            return dataType;
        }

        public void setDataType(String dataType) {
            this.dataType = dataType;
        }

        public Boolean getIsParentLink() {
            return isParentLink;
        }

        public void setIsParentLink(Boolean isParentLink) {
            this.isParentLink = isParentLink;
        }

        public IssueLinkMapping getIssueLink() {
            return issueLink;
        }

        public void setIssueLink(IssueLinkMapping issueLink) {
            this.issueLink = issueLink;
        }
    }

    /**
     * Represents the special configuration for issue link processing.
     * This is used when a field represents a Jira issue link (e.g., Dependant Issues).
     */
    public static class IssueLinkMapping {
        private boolean isInward;
        private List<String> linkTypes;

        public boolean isInward() {
            return isInward;
        }

        public void setIsInward(boolean isInward) {
            this.isInward = isInward;
        }

        public List<String> getLinkTypes() {
            return linkTypes;
        }

        public void setLinkTypes(List<String> linkTypes) {
            this.linkTypes = linkTypes;
        }
    }
}
