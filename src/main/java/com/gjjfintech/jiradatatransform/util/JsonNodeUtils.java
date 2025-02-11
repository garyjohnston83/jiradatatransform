package com.gjjfintech.jiradatatransform.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public class JsonNodeUtils {

    /**
     * Extracts a nested JsonNode from a given JsonNode based on a dotted path.
     * For example, if the path is "fields.summary", it will return node.at("/fields/summary").
     *
     * @param node the root JsonNode
     * @param dottedPath a dotted path representing the nested attribute
     * @return the JsonNode at the specified path, or a missing node if not found.
     */
    public static JsonNode getJsonNodeAt(JsonNode node, String dottedPath) {
        String jsonPointer = "/" + dottedPath.replace(".", "/");
        return node.at(jsonPointer);
    }

    /**
     * Processes a JsonNode expected to represent an array of strings.
     */
    public static List<String> processStringArrayValue(JsonNode valueNode) {
        List<String> result = new ArrayList<>();
        if (valueNode.isArray()) {
            for (JsonNode element : valueNode) {
                result.add(element.asText());
            }
        } else {
            result.add(valueNode.asText());
        }
        return result;
    }
}