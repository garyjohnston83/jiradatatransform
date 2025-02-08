package com.gjjfintech.jiradatatransform.util;

import com.fasterxml.jackson.databind.JsonNode;

public class JsonPathUtils {

    /**
     * Extracts a nested JsonNode from a given JsonNode based on a dotted path.
     * For example, if the path is "fields.summary", it will return node.at("/fields/summary").
     *
     * @param node the root JsonNode
     * @param dottedPath a dotted path representing the nested attribute
     * @return the JsonNode at the specified path, or a missing node if not found.
     */
    public static JsonNode getValue(JsonNode node, String dottedPath) {
        String jsonPointer = "/" + dottedPath.replace(".", "/");
        return node.at(jsonPointer);
    }
}