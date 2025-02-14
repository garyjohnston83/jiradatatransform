package com.gjjfintech.jiradatatransform.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtils {

    /**
     * Converts a dotted path (e.g., "fields.summary") to a JSON pointer (e.g., "/fields/summary").
     */
    public static String convertToJsonPointer(String dottedPath) {
        return "/" + dottedPath.replace(".", "/");
    }

    /**
     * Converts a human-readable field name (e.g., "Issue Key") into camelCase (e.g., "issueKey").
     */
    public static String toCamelCase(String input) {
        StringBuilder sb = new StringBuilder();
        boolean nextCapital = false;
        for (char c : input.toCharArray()) {
            if (Character.isWhitespace(c) || c == '-' || c == '_') {
                nextCapital = true;
            } else {
                if (sb.length() == 0) {
                    sb.append(Character.toLowerCase(c));
                } else if (nextCapital) {
                    sb.append(Character.toUpperCase(c));
                    nextCapital = false;
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    /**
     * Extracts all risk IDs from the given input string.
     * A risk ID is defined as one or more letters followed by a hyphen and one or more digits.
     *
     * @param input the input string containing risk IDs.
     * @return a List of risk IDs found in the input string.
     */
    public static List<String> extractRiskIds(String input) {
        List<String> riskIds = new ArrayList<>();
        // Define a regular expression for risk IDs: letters, hyphen, digits.
        Pattern pattern = Pattern.compile("[A-Za-z]+-\\d+");
        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            riskIds.add(matcher.group());
        }
        return riskIds;
    }
}