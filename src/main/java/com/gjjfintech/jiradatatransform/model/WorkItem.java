package com.gjjfintech.jiradatatransform.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class WorkItem {

    @JsonProperty("Feature Priority")
    private String featurePriority;

    @JsonProperty("Feature Name")
    private String featureName;

    @JsonProperty("Epic Name")
    private String epicName;

    @JsonProperty("Epic Link")
    private String epicLink;

    @JsonProperty("Risk Finding Ids")
    private List<String> riskFindingIds;

    @JsonProperty("Planned")
    private boolean planned;

    @JsonProperty("DevOps")
    private double devOps;

    @JsonProperty("Engineering")
    private double engineering;

    @JsonProperty("Architecture")
    private double architecture;

    @JsonProperty("Other")
    private double other;

    @JsonProperty("Status")
    private String status;

    // Getters and Setters

    public String getFeaturePriority() {
        return featurePriority;
    }

    public void setFeaturePriority(String featurePriority) {
        this.featurePriority = featurePriority;
    }

    public String getFeatureName() {
        return featureName;
    }

    public void setFeatureName(String featureName) {
        this.featureName = featureName;
    }

    public String getEpicName() {
        return epicName;
    }

    public void setEpicName(String epicName) {
        this.epicName = epicName;
    }

    public String getEpicLink() {
        return epicLink;
    }

    public void setEpicLink(String epicLink) {
        this.epicLink = epicLink;
    }

    public List<String> getRiskFindingIds() {
        return riskFindingIds;
    }

    public void setRiskFindingIds(List<String> riskFindingIds) {
        this.riskFindingIds = riskFindingIds;
    }

    public boolean isPlanned() {
        return planned;
    }

    public void setPlanned(boolean planned) {
        this.planned = planned;
    }

    public double getDevOps() {
        return devOps;
    }

    public void setDevOps(double devOps) {
        this.devOps = devOps;
    }

    public double getEngineering() {
        return engineering;
    }

    public void setEngineering(double engineering) {
        this.engineering = engineering;
    }

    public double getArchitecture() {
        return architecture;
    }

    public void setArchitecture(double architecture) {
        this.architecture = architecture;
    }

    public double getOther() {
        return other;
    }

    public void setOther(double other) {
        this.other = other;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "WorkItem{" +
                "featurePriority='" + featurePriority + '\'' +
                ", featureName='" + featureName + '\'' +
                ", epicName='" + epicName + '\'' +
                ", epicLink='" + epicLink + '\'' +
                ", riskFindingIds=" + riskFindingIds +
                ", planned=" + planned +
                ", devOps=" + devOps +
                ", engineering=" + engineering +
                ", architecture=" + architecture +
                ", other=" + other +
                ", status='" + status + '\'' +
                '}';
    }
}