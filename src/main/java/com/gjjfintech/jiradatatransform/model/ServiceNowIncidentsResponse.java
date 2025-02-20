package com.gjjfintech.jiradatatransform.model;

import java.util.List;

public class ServiceNowIncidentsResponse {
    private List<ServiceNowIncident> result;

    public List<ServiceNowIncident> getResult() {
        return result;
    }

    public void setResult(List<ServiceNowIncident> result) {
        this.result = result;
    }
}
