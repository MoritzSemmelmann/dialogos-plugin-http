package com.clt.dialogos.jsonplugin;

import com.clt.diamant.Slot;
import com.clt.script.exp.Value;
import com.clt.script.exp.values.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.function.Function;

/**
 * Utility class for converting between DialogOS Value objects and JSON objects.
 */
public class JsonConverter {

    public static Object valueToJson(Value value) {
        String jsonString = value.toJson();
        
        if (jsonString.startsWith("{")) {
            return new JSONObject(jsonString);
        } else if (jsonString.startsWith("[")) {
            return new JSONArray(jsonString);
        } else if (jsonString.equals("null")) {
            return JSONObject.NULL;
        } else if (jsonString.equals("true") || jsonString.equals("false")) {
            return Boolean.parseBoolean(jsonString);
        } else if (jsonString.startsWith("\"")) {
            return jsonString.substring(1, jsonString.length() - 1);
        } else {
            try {
                if (jsonString.contains(".")) {
                    return Double.parseDouble(jsonString);
                } else {
                    return Integer.parseInt(jsonString);
                }
            } catch (NumberFormatException e) {
                return jsonString;
            }
        }
    }

    public static Value jsonToValue(Object json) {
        if (json == null || json == JSONObject.NULL) {
            return new Undefined();
        } else if (json instanceof JSONObject) {
            return Value.fromJson((JSONObject) json);
        } else if (json instanceof JSONArray) {
            return Value.fromJson((JSONArray) json);
        } else if (json instanceof String) {
            return new StringValue((String) json);
        } else if (json instanceof Boolean) {
            return new BoolValue((Boolean) json);
        } else if (json instanceof Integer) {
            return new IntValue((Integer) json);
        } else if (json instanceof Long) {
            return new IntValue(((Long) json).intValue());
        } else if (json instanceof Double) {
            return new RealValue((Double) json);
        } else {
            return new StringValue(json.toString());
        }
    }

    public static JSONObject variablesToJson(String[] varNames, Function<String, Slot> slotProvider) {
        JSONObject jsonObject = new JSONObject();
        
        for (String varName : varNames) {
            varName = varName.trim();
            Slot slot = slotProvider.apply(varName);
            if (slot != null) {
                Value value = slot.getValue();
                Object jsonValue = valueToJson(value);
                jsonObject.put(varName, jsonValue);
            }
        }
        
        return jsonObject;
    }


    public static JSONObject structToJson(StructValue struct) {
        String jsonString = struct.toJson();
        return new JSONObject(jsonString);
    }

    public static void mapJsonToVariables(JSONObject responseJson, String mappingsStr, Function<String, Slot> slotProvider) {
        if (mappingsStr == null || mappingsStr.trim().isEmpty()) {
            System.out.println("Warning: No response variable mappings specified");
            return;
        }
        
        String[] mappings = mappingsStr.split(",");
        for (String mapping : mappings) {
            mapping = mapping.trim();
            if (mapping.isEmpty()) continue;
            
            String[] parts = mapping.split("=");
            if (parts.length != 2) {
                System.out.println("Warning: Invalid mapping format '" + mapping + "', expected 'jsonKey=varName'");
                continue;
            }
            
            String jsonKey = parts[0].trim();
            String varName = parts[1].trim();
            
            try {
                if (responseJson.has(jsonKey)) {
                    Object jsonValue = responseJson.get(jsonKey);
                    String jsonString = (jsonValue instanceof JSONObject || jsonValue instanceof JSONArray) 
                        ? jsonValue.toString() 
                        : String.valueOf(jsonValue);
                    Value dialogosValue = Value.fromJson(jsonString);
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

    public static void mapJsonToSingleVariable(JSONObject responseJson, String targetVarName, boolean asString, Function<String, Slot> slotProvider) {
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
                // Store as String
                value = new StringValue(responseJson.toString());
                System.out.println("Response stored as String in variable '" + targetVarName + "'");
            } else {
                // Store as Struct
                value = Value.fromJson(responseJson.toString());
                System.out.println("Response stored as Struct in variable '" + targetVarName + "'");
            }
            
            targetSlot.setValue(value);
        } catch (Exception e) {
            System.out.println("ERROR: Failed to map JSON to variable '" + targetVarName + "': " + e.getMessage());
        }
    }
}
