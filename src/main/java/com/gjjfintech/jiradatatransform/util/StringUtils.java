package com.gjjfintech.jiradatatransform.util;

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
}