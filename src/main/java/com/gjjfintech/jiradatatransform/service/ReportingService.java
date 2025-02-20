package com.gjjfintech.jiradatatransform.service;

import com.gjjfintech.jiradatatransform.client.ConfluenceApiClient;
import com.gjjfintech.jiradatatransform.client.ServiceNowApiClient;
import com.gjjfintech.jiradatatransform.model.CreateConfluencePageRequestBody;
import com.gjjfintech.jiradatatransform.model.ServiceNowIncident;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

@Service
public class ReportingService {

    private final ServiceNowApiClient serviceNowApiClient;
    private final ConfluenceApiClient confluenceApiClient;

    public ReportingService(ServiceNowApiClient serviceNowApiClient, ConfluenceApiClient confluenceApiClient) {
        this.serviceNowApiClient = serviceNowApiClient;
        this.confluenceApiClient = confluenceApiClient;
    }

    /**
     * Creates a Confluence page containing a table of incidents retrieved from ServiceNow.
     *
     * @param sysparmQuery the filter query for ServiceNow (e.g., "active=true")
     * @param title        the title for the Confluence page
     * @param spaceKey     the Confluence space key
     * @param parentPageId the parent page id under which the new page will be created
     */
    public void createIncidentsPage(String sysparmQuery, String title, String spaceKey, String parentPageId) {
        // 1. Retrieve incidents from ServiceNow.
        List<ServiceNowIncident> incidents = serviceNowApiClient.getIncidents(sysparmQuery);

        // 2. Build the HTML string for the table.
        StringBuilder htmlBuilder = new StringBuilder();
        htmlBuilder.append("<table border='1' style='border-collapse: collapse;'>");

        // Retrieve all declared fields from IncidentDto.
        Field[] fields = ServiceNowIncident.class.getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
        }

        // Create header row.
        htmlBuilder.append("<tr>");
        for (Field field : fields) {
            String headerName = standardiseColumnName(field.getName());
            htmlBuilder.append("<th>").append(headerName).append("</th>");
        }
        htmlBuilder.append("</tr>");

        // Create a row for each incident.
        for (ServiceNowIncident incident : incidents) {
            htmlBuilder.append("<tr>");
            for (Field field : fields) {
                try {
                    Object value = field.get(incident);
                    htmlBuilder.append("<td>")
                            .append(value != null ? value.toString() : "")
                            .append("</td>");
                } catch (IllegalAccessException e) {
                    htmlBuilder.append("<td></td>");
                }
            }
            htmlBuilder.append("</tr>");
        }
        htmlBuilder.append("</table>");

        String htmlContent = htmlBuilder.toString();

        // 3. Create a Confluence page with the generated HTML.
        CreateConfluencePageRequestBody.Ancestor ancestor = new CreateConfluencePageRequestBody.Ancestor(parentPageId);
        CreateConfluencePageRequestBody.Space space = new CreateConfluencePageRequestBody.Space(spaceKey);
        CreateConfluencePageRequestBody.Body.Storage storage = new CreateConfluencePageRequestBody.Body.Storage(htmlContent, "storage");
        CreateConfluencePageRequestBody.Body body = new CreateConfluencePageRequestBody.Body(storage);

        CreateConfluencePageRequestBody createPageDTO = new CreateConfluencePageRequestBody();
        createPageDTO.setType("page");
        createPageDTO.setTitle(title);
        createPageDTO.setAncestors(Collections.singletonList(ancestor));
        createPageDTO.setSpace(space);
        createPageDTO.setBody(body);

        // 4. Call the Confluence API to create the page.
        confluenceApiClient.createPage(createPageDTO);
    }

    /**
     * Converts a camelCase or underscore_delimited string into a
     * human-readable form with spaces and each word capitalized.
     * E.g., "shortDescription" or "short_description" becomes "Short Description".
     *
     * @param name the original field name
     * @return the standardized column name
     */
    public static String standardiseColumnName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        // Replace underscores with spaces.
        name = name.replace("_", " ");

        // Insert a space before each uppercase letter following a lowercase letter.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && Character.isLowerCase(name.charAt(i - 1))) {
                sb.append(" ");
            }
            sb.append(c);
        }

        // Capitalize each word.
        String[] words = sb.toString().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        return result.toString().trim();
    }
}

