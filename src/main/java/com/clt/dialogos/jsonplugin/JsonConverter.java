package com.clt.dialogos.jsonplugin;

import com.clt.diamant.Slot;
import com.clt.script.exp.Value;
import com.clt.script.exp.values.*;
import org.json.JSONArray;
import org.json.JSONObject;

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

    public static JSONObject variablesToJson(String[] varNames, Function<String, Slot> slotProvider) {
        JSONObject jsonObject = new JSONObject();
        
        for (String varName : varNames) {
            varName = varName.trim();
            Slot slot = slotProvider.apply(varName);
            if (slot != null) {
                Value value = slot.getValue();
                String jsonStr = value.toJson();
                Object jsonValue = parseJsonString(jsonStr);
                jsonObject.put(varName, jsonValue);
            }
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
            return jsonStr;
        }
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
                value = new StringValue(responseJson.toString());
                System.out.println("Response stored as String in variable '" + targetVarName + "'");
            } else {
                value = Value.fromJson(responseJson);
                System.out.println("Response stored as Struct in variable '" + targetVarName + "'");
            }
            
            targetSlot.setValue(value);
        } catch (Exception e) {
            System.out.println("ERROR: Failed to map JSON to variable '" + targetVarName + "': " + e.getMessage());
        }
    }
}
