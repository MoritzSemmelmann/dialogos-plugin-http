package com.clt.dialogos.httpplugin;

import com.clt.diamant.Slot;
import com.clt.script.DefaultEnvironment;
import com.clt.script.exp.Expression;
import com.clt.script.exp.Type;
import com.clt.script.exp.Value;
import com.clt.script.exp.Variable;
import com.clt.script.exp.values.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;
import java.util.function.Function;

public class JsonConverter {
    public static Object valueToJson(Value value) {
        String jsonString = value.toJson();
        
        if (jsonString.startsWith("{")) {
            return new JSONObject(jsonString);
        } else if (jsonString.startsWith("[")) {
            return new JSONArray(jsonString);
        } else {
            return jsonString;
        }
    }

    
    public static Value jsonToValue(Object json) {
        if (json == null || json == JSONObject.NULL) {
            return new Undefined();
        } else if (json instanceof JSONObject) {
            return Value.fromJson((JSONObject) json);
        } else if (json instanceof JSONArray) {
            return Value.fromJson((JSONArray) json);
        } else {
            JSONObject wrapper = new JSONObject();
            wrapper.put("temp", json);
            StructValue struct = (StructValue) Value.fromJson(wrapper);
            return struct.getValue("temp");
        }
    }

    public static JSONObject variablesToJson(Map<String, String> keyToVarMappings, Function<String, Slot> slotProvider) {
        JSONObject jsonObject = new JSONObject();
        
        for (Map.Entry<String, String> entry : keyToVarMappings.entrySet()) {
            String jsonKey = entry.getKey();
            String expression = entry.getValue();
            
            if (expression == null || expression.trim().isEmpty()) {
                continue;
            }
            
            Value value = evaluateExpression(expression, slotProvider);
            Object jsonValue;
            if (value instanceof StringValue) {
                jsonValue = interpretStringSlot(((StringValue) value).getString());
            } else {
                jsonValue = parseJsonString(value.toJson());
            }
            jsonObject.put(jsonKey, jsonValue);
        }
        
        return jsonObject;
    }

    public static JSONObject structToJson(StructValue struct) {
        return new JSONObject(struct.toJson());
    }

    private static Object parseJsonString(String jsonStr) {
        if (jsonStr.startsWith("{")) {
            return new JSONObject(jsonStr);
        } else if (jsonStr.startsWith("[")) {
            return new JSONArray(jsonStr);
        } else {
            try {
                JSONObject temp = new JSONObject("{\"v\":" + jsonStr + "}");
                return temp.get("v");
            } catch (Exception e) {
                return jsonStr;
            }
        }
    }

    private static Object interpretStringSlot(String raw) {
        if (raw == null) {
            return JSONObject.NULL;
        }

        String trimmed = raw.trim();
        if (!trimmed.isEmpty()) {
            if ((trimmed.startsWith("{") && trimmed.endsWith("}"))) {
                try {
                    return new JSONObject(trimmed);
                } catch (Exception ignored) {
                    // fall through and treat as plain string
                }
            } else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                try {
                    return new JSONArray(trimmed);
                } catch (Exception ignored) {
                    // fall through and treat as plain string
                }
            }
        }

        return raw;
    }
    
    public static Value evaluateExpression(String expressionStr, Function<String, Slot> slotProvider) {
        if (expressionStr == null || expressionStr.trim().isEmpty()) {
            return new StringValue("");
        }
        
        try {
            Expression expr = Expression.parseExpression(expressionStr, new DefaultEnvironment() {
                @Override
                public Variable createVariableReference(String id) {
                    Slot slot = slotProvider.apply(id);
                    if (slot != null) {
                        return new Variable() {
                            public String getName() { return id; }
                            public Value getValue() { return slot.getValue(); }
                            public void setValue(Value value) { slot.setValue(value); }
                            public Type getType() { return slot.getValue().getType(); }
                        };
                    }
                    return super.createVariableReference(id);
                }
            });
            
            Value result = expr.evaluate();
            System.out.println("Expression '" + expressionStr + "' evaluated to: " + result.toString());
            return result;
        } catch (Exception e) {
            System.out.println("Expression parsing failed for '" + expressionStr + "', treating as string literal: " + e.getMessage());
            return new StringValue(expressionStr);
        }
    }

    private static final Object JSON_PATH_NOT_FOUND = new Object();

    public static void mapJsonToVariables(JSONObject responseJson, String mappingsStr, Function<String, Slot> slotProvider) {
        if (mappingsStr == null || mappingsStr.trim().isEmpty()) {
            System.out.println("Warning: No response variable mappings specified");
            return;
        }
        
        String[] mappings = mappingsStr.split(",");
        for (String mapping : mappings) {
            mapping = mapping.trim();
            if (mapping.isEmpty()) continue;
            
            String[] parts = mapping.split("=", 2);
            if (parts.length != 2) {
                System.out.println("Warning: Invalid mapping format '" + mapping + "', expected 'jsonKey=varName'");
                continue;
            }
            
            String jsonKey = parts[0].trim();
            String varName = parts[1].trim();
            
            try {
                Object jsonValue = resolveJsonPath(responseJson, jsonKey);
                if (jsonValue != JSON_PATH_NOT_FOUND) {
                    Value dialogosValue = jsonToValue(jsonValue);
                    Slot targetSlot = slotProvider.apply(varName);
                    if (targetSlot != null) {
                        targetSlot.setValue(dialogosValue);
                        System.out.println("  " + jsonKey + " -> " + varName + ": " + dialogosValue + " (" + dialogosValue.getType() + ")");
                    } else {
                        System.out.println("  ERROR: Variable '" + varName + "' not found");
                    }
                } else {
                    System.out.println("  " + jsonKey + " not found in response JSON (skipping " + varName + ")");
                }
            } catch (Exception e) {
                System.out.println("  ERROR: " + jsonKey + " -> " + varName + ": " + e.getMessage());
            }
        }
    }

    public static void mapJsonToSingleVariable(Object responsePayload, String rawResponse, String targetVarName, boolean asString, Function<String, Slot> slotProvider) {
        if (targetVarName == null || targetVarName.trim().isEmpty()) {
            System.out.println("Warning: No target variable specified");
            return;
        }
        
        try {
            Slot targetSlot = slotProvider.apply(targetVarName.trim());
            if (targetSlot == null) {
                System.out.println("ERROR: Variable '" + targetVarName + "' not found");
                return;
            }
            
            Value value;
            if (asString) {
                String storeValue = rawResponse != null ? rawResponse : (responsePayload != null ? responsePayload.toString() : "");
                value = new StringValue(storeValue);
                System.out.println("Response stored as String in variable '" + targetVarName + "'");
            } else {
                if (responsePayload instanceof JSONObject) {
                    value = Value.fromJson((JSONObject) responsePayload);
                    System.out.println("Response stored as Struct in variable '" + targetVarName + "'");
                } else if (responsePayload instanceof JSONArray) {
                    value = Value.fromJson((JSONArray) responsePayload);
                    System.out.println("Response stored as List in variable '" + targetVarName + "'");
                } else {
                    value = new Undefined();
                    System.out.println("Response payload was empty; stored Undefined in variable '" + targetVarName + "'");
                }
            }
            
            targetSlot.setValue(value);
        } catch (Exception e) {
            System.out.println("ERROR: Failed to map JSON to variable '" + targetVarName + "': " + e.getMessage());
        }
    }

    private static Object resolveJsonPath(Object current, String path) {
        if (path == null || path.isEmpty()) {
            return current;
        }

        if (path.charAt(0) == '[' && current instanceof JSONObject) {
            JSONObject obj = (JSONObject) current;
            if (obj.has("$root")) {
                current = obj.get("$root");
            }
        }

        int pos = 0;
        int length = path.length();
        while (pos < length) {
            if (path.charAt(pos) == '.') {
                pos++;
                continue;
            }

            int segmentStart = pos;
            while (pos < length && path.charAt(pos) != '.' && path.charAt(pos) != '[') {
                pos++;
            }

            if (segmentStart != pos) {
                String key = path.substring(segmentStart, pos);
                if (!(current instanceof JSONObject)) {
                    return JSON_PATH_NOT_FOUND;
                }
                JSONObject obj = (JSONObject) current;
                if (!obj.has(key)) {
                    return JSON_PATH_NOT_FOUND;
                }
                current = obj.get(key);
            }

            while (pos < length && path.charAt(pos) == '[') {
                pos++;
                int closingBracket = path.indexOf(']', pos);
                if (closingBracket == -1) {
                    return JSON_PATH_NOT_FOUND;
                }
                String indexStr = path.substring(pos, closingBracket).trim();
                int index;
                try {
                    index = Integer.parseInt(indexStr);
                } catch (NumberFormatException ex) {
                    return JSON_PATH_NOT_FOUND;
                }
                if (!(current instanceof JSONArray)) {
                    return JSON_PATH_NOT_FOUND;
                }
                JSONArray array = (JSONArray) current;
                if (index < 0 || index >= array.length()) {
                    return JSON_PATH_NOT_FOUND;
                }
                current = array.get(index);
                pos = closingBracket + 1;
            }

            if (pos < length && path.charAt(pos) == '.') {
                pos++;
            }
        }

        return current;
    }
}
