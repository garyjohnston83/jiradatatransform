package com.gjjfintech.jiradatatransform.config;

import java.util.List;
import java.util.Map;

/**
 * A plain POJO representing the Jira mapping configuration.
 * This class is used to bind the contents of a YAML file (source or destination)
 * into an instance of this type via our binder utility (JiraMappingPropertiesBinder).
 */
public class JiraMappingProperties {

    private Map<String, FieldMapping> jiraFieldMappings;

    public Map<String, FieldMapping> getJiraFieldMappings() {
        return jiraFieldMappings;
    }

    public void setJiraFieldMappings(Map<String, FieldMapping> jiraFieldMappings) {
        this.jiraFieldMappings = jiraFieldMappings;
    }

    /**
     * Represents the configuration for a single field mapping.
     */
    public static class FieldMapping {
        private String issueAttributeName;
        private String issueColumnName;
        private String dataType;
        private Boolean isParentLink;
        // NEW: Flag indicating that this field is used for linking between instances.
        private Boolean isLinkingId;
        private IssueLinkMapping issueLink;

        public String getIssueAttributeName() {
            return issueAttributeName;
        }

        public void setIssueAttributeName(String issueAttributeName) {
            this.issueAttributeName = issueAttributeName;
        }

        public String getIssueColumnName() {
            return issueColumnName;
        }

        public void setIssueColumnName(String issueColumnName) {
            this.issueColumnName = issueColumnName;
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

        public Boolean getIsLinkingId() {
            return isLinkingId;
        }

        public void setIsLinkingId(Boolean isLinkingId) {
            this.isLinkingId = isLinkingId;
        }

        public IssueLinkMapping getIssueLink() {
            return issueLink;
        }

        public void setIssueLink(IssueLinkMapping issueLink) {
            this.issueLink = issueLink;
        }
    }

    /**
     * Represents the special configuration for processing issue links.
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