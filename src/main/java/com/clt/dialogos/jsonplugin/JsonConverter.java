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
        if (value instanceof com.clt.script.exp.values.Undefined) {
            return JSONObject.NULL;
        } else if (value instanceof IntValue) {
            return ((IntValue) value).getInt();
        } else if (value instanceof RealValue) {
            return ((RealValue) value).getReal();
        } else if (value instanceof BoolValue) {
            return ((BoolValue) value).getBool();
        } else if (value instanceof StringValue) {
            return ((StringValue) value).getString();
        } else if (value instanceof ListValue) {
            ListValue list = (ListValue) value;
            JSONArray arr = new JSONArray();
            for (int i = 0; i < list.size(); i++) {
                arr.put(valueToJson(list.get(i)));
            }
            return arr;
        } else if (value instanceof StructValue) {
            StructValue struct = (StructValue) value;
            JSONObject obj = new JSONObject();
            for (String key : struct.getLabels()) {
                obj.put(key, valueToJson(struct.getValue(key)));
            }
            return obj;
        } else {
            return value.toString();
        }
    }

    public static Value jsonToValue(Object json) {
        if (json == null || json == JSONObject.NULL) {
            return new Undefined();
        } else if (json instanceof JSONObject) {
            JSONObject obj = (JSONObject) json;
            String[] labels = new String[obj.length()];
            Value[] values = new Value[obj.length()];
            int i = 0;
            for (String key : obj.keySet()) {
                labels[i] = key;
                values[i] = jsonToValue(obj.get(key));
                i++;
            }
            return new StructValue(labels, values);
        } else if (json instanceof JSONArray) {
            JSONArray arr = (JSONArray) json;
            Value[] values = new Value[arr.length()];
            for (int i = 0; i < arr.length(); i++) {
                values[i] = jsonToValue(arr.get(i));
            }
            return new ListValue(values);
        } else if (json instanceof Boolean) {
            return new BoolValue((Boolean) json);
        } else if (json instanceof Integer) {
            return new IntValue((Integer) json);
        } else if (json instanceof Long) {
            return new IntValue(((Long) json).intValue());
        } else if (json instanceof Double) {
            return new RealValue((Double) json);
        } else if (json instanceof String) {
            return new StringValue((String) json);
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
        JSONObject obj = new JSONObject();
        for (String key : struct.getLabels()) {
            obj.put(key, valueToJson(struct.getValue(key)));
        }
        return obj;
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

    /**
     * Maps entire JSON response to a single variable as Struct or String.
     * @param responseJson The JSON response object
     * @param targetVarName The target variable name
     * @param asString If true, stores as String; if false, stores as StructValue
     * @param slotProvider Function to get a slot by variable name
     */
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
                value = jsonToValue(responseJson);
                System.out.println("Response stored as Struct in variable '" + targetVarName + "'");
            }
            
            targetSlot.setValue(value);
        } catch (Exception e) {
            System.out.println("ERROR: Failed to map JSON to variable '" + targetVarName + "': " + e.getMessage());
        }
    }
}
