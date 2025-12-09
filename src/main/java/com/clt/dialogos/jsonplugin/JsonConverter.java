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
}
