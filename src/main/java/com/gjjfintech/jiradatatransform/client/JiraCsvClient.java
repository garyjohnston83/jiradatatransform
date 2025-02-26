package com.gjjfintech.jiradatatransform.client;

import com.gjjfintech.jiradatatransform.config.JiraMappingProperties;
import com.gjjfintech.jiradatatransform.util.StringUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.Reader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class JiraCsvClient {

    /**
     * Reads issues from a CSV file and converts them to a collection of flattened maps.
     *
     * @param filePath     The path to the CSV file.
     * @param mappingProps The Jira mapping configuration.
     * @return A collection of flattened issue maps.
     */
    public Collection<Map<String, Object>> getIssuesByFile(String filePath, JiraMappingProperties mappingProps) {
        List<CSVRecord> records = readCsv(filePath);
        List<Map<String, Object>> issues = new ArrayList<>();
        for (CSVRecord record : records) {
            Map<String, Object> flatIssue = convertCsvRow(record, mappingProps);
            issues.add(flatIssue);
        }
        return issues;
    }

    /**
     * Reads a CSV file and returns a list of CSVRecord objects.
     * This method uses Apache Commons CSV with headers.
     *
     * @param filePath The path to the CSV file.
     * @return A list of CSVRecord objects.
     */
    private List<CSVRecord> readCsv(String filePath) {
        List<CSVRecord> recordsList = new ArrayList<>();
        try (Reader in = new FileReader(filePath)) {
            Iterable<CSVRecord> records = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .parse(in);
            for (CSVRecord record : records) {
                recordsList.add(record);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error reading CSV file: " + filePath, e);
        }
        return recordsList;
    }

    /**
     * Converts a CSVRecord into a flattened issue map based on the provided mapping configuration.
     *
     * For each mapping, it looks for the CSV column name specified by issueColumnName.
     * If the mapping's dataType starts with "String[]", it collects all values for that header.
     * Otherwise, it retrieves a single value.
     *
     * The resulting map's keys are the camelCase version of the display name.
     *
     * @param record       The CSVRecord.
     * @param mappingProps The Jira mapping configuration.
     * @return a flattened issue map.
     */
    private Map<String, Object> convertCsvRow(CSVRecord record, JiraMappingProperties mappingProps) {
        Map<String, Object> flat = new HashMap<>();
        for (Map.Entry<String, JiraMappingProperties.FieldMapping> entry : mappingProps.getJiraFieldMappings().entrySet()) {
            String displayName = entry.getKey();
            JiraMappingProperties.FieldMapping mapping = entry.getValue();
            // Use the CSV column defined by issueColumnName.
            String columnName = mapping.getIssueColumnName();
            if (columnName != null && record.isMapped(columnName)) {
                String dataType = mapping.getDataType();
                String flatKey = StringUtils.toCamelCase(displayName);
                if (dataType != null && dataType.startsWith("String[]")) {
                    // Use our helper method to get all values for this header.
                    List<String> rawValues = getValuesForHeader(record, columnName);
                    List<String> processedValues = new ArrayList<>();
                    for (String rawValue : rawValues) {
                        if(rawValue != null && !rawValue.isEmpty()) {
                            processedValues.add(processStringValue(rawValue, dataType));
                        }
                    }
                    if(!processedValues.isEmpty()) {
                        flat.put(flatKey, processedValues);
                    }
                } else {
                    // Single value.
                    String rawValue = record.get(columnName);
                    String processedValue = processStringValue(rawValue, dataType);
                    if(processedValue != null && !processedValue.isEmpty()) {
                        flat.put(flatKey, processedValue);
                    }

                }
            }
        }
        return flat;
    }

    /**
     * Helper method to retrieve all values from a CSVRecord for a given header name.
     * Iterates over the list of header names and collects cell values where the header matches.
     *
     * @param record     The CSVRecord.
     * @param columnName The column header to look for.
     * @return A list of values for that header.
     */
    private List<String> getValuesForHeader(CSVRecord record, String columnName) {
        List<String> values = new ArrayList<>();
        List<String> headers = record.getParser().getHeaderNames();
        // Iterate over header names and compare (case-insensitively).
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).equalsIgnoreCase(columnName)) {
                values.add(record.get(i));
            }
        }
        return values;
    }

    /**
     * Processes a raw string value based on the specified dataType.
     * For "DateAsString[pattern]", it attempts to parse and reformat the date.
     * For other types, it simply returns the value.
     *
     * @param value    the raw string value.
     * @param dataType the dataType specification.
     * @return the processed string.
     */
    private String processStringValue(String value, String dataType) {
        if (dataType == null || (dataType.startsWith("String") && !dataType.startsWith("String["))) {
            return value;
        } else if (dataType.startsWith("DateAsString")) {
            // Extract the pattern from within brackets.
            String pattern = dataType.substring(dataType.indexOf('[') + 1, dataType.indexOf(']'));
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern);
                Date date = sdf.parse(value);
                return sdf.format(date);
            } catch (ParseException e) {
                return value;
            }
        }
        return value;
    }
}
