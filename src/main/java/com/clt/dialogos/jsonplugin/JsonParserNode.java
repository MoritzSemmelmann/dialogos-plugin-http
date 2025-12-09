package com.clt.dialogos.jsonplugin;

import com.clt.diamant.*;
import com.clt.diamant.graph.Graph;
import com.clt.diamant.graph.Node;
import com.clt.diamant.graph.nodes.NodeExecutionException;
import com.clt.diamant.gui.NodePropertiesDialog;
import com.clt.script.exp.Type;
import com.clt.script.exp.Value;
import com.clt.script.exp.types.StructType;
import com.clt.script.exp.values.*;
import com.clt.xml.XMLReader;
import com.clt.xml.XMLWriter;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JsonParserNode extends Node {

    private static final String SOURCE_VAR = "sourceVar";
    private static final String VARIABLE_MAPPINGS = "variableMappings";

    public JsonParserNode() {
        this.addEdge();
        this.setProperty(SOURCE_VAR, "");
        this.setProperty(VARIABLE_MAPPINGS, "");
    }

    public static String getNodeTypeName(Class<?> c) {
        return "JSON Parser";
    }

    @Override
    public Node execute(WozInterface comm, InputCenter input, ExecutionLogger logger) {
        logNode(logger);

        try {
            Slot sourceVar = (Slot) this.getProperty(SOURCE_VAR);
            if (sourceVar == null) {
                throw new NodeExecutionException(this, "No source variable specified");
            }

            Value sourceValue = sourceVar.getValue();
            JSONObject jsonObject;

            if (sourceValue instanceof StringValue) {
                String jsonString = ((StringValue) sourceValue).getString();
                jsonObject = new JSONObject(jsonString);
                System.out.println("JSON Parser: Parsing from String");
            } else if (sourceValue instanceof StructValue) {
                // Convert StructValue back to JSON
                jsonObject = structToJson((StructValue) sourceValue);
                System.out.println("JSON Parser: Parsing from StructValue");
            } else {
                throw new NodeExecutionException(this, "Source variable must be String or Struct, but is: " + sourceValue.getType());
            }

            System.out.println("JSON: " + jsonObject.toString(2));

            String mappingsStr = this.getProperty(VARIABLE_MAPPINGS).toString().trim();
            if (mappingsStr.isEmpty()) {
                System.out.println("Warning: No variable mappings specified");
                return getEdge(0).getTarget();
            }

            String[] mappings = mappingsStr.split(",");
            System.out.println("\n--- Extracting Values ---");

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
                    if (jsonObject.has(jsonKey)) {
                        Object jsonValue = jsonObject.get(jsonKey);
                        Value dialogosValue = jsonToValue(jsonValue);
                        
                        Slot targetSlot = getSlot(varName);
                        targetSlot.setValue(dialogosValue);
                        
                        System.out.println("  " + jsonKey + " -> " + varName + ": " + dialogosValue + " (" + dialogosValue.getType() + ")");
                    } else {
                        System.out.println("  " + jsonKey + " not found in JSON (skipping " + varName + ")");
                    }
                } catch (Exception e) {
                    System.out.println("  ERROR: " + jsonKey + " -> " + varName + ": " + e.getMessage());
                }
            }

        } catch (NodeExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new NodeExecutionException(this, "Failed to parse JSON: " + e.getMessage(), e);
        }

        return getEdge(0).getTarget();
    }

    private JSONObject structToJson(StructValue struct) {
        JSONObject obj = new JSONObject();
        for (String key : struct.getLabels()) {
            Value value = struct.getValue(key);
            obj.put(key, valueToJson(value));
        }
        return obj;
    }

    private Object valueToJson(Value value) {
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
            return structToJson((StructValue) value);
        } else {
            return value.toString();
        }
    }

    private Value jsonToValue(Object json) {
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

    private Slot getSlot(String name) {
        List<Slot> slots = this.getGraph().getAllVariables(Graph.LOCAL);
        for (Slot slot : slots) {
            if (name.equals(slot.getName()))
                return slot;
        }
        throw new NodeExecutionException(this, "Unable to find variable: " + name);
    }

    @Override
    public JComponent createEditorComponent(Map<String, Object> properties) {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(5, 5, 5, 5);

        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0.3;
        p.add(new JLabel("JSON source variable:"), c);

        c.gridx = 1;
        c.weightx = 1.0;
        List<Slot> allVars = this.getGraph().getAllVariables(Graph.LOCAL);
        List<Slot> filteredVars = new ArrayList<>();
        for (Slot slot : allVars) {
            Type type = slot.getType();
            if (type == Type.String || type instanceof StructType) {
                filteredVars.add(slot);
            }
        }
        p.add(NodePropertiesDialog.createComboBox(properties, SOURCE_VAR, filteredVars), c);

        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0.3;
        p.add(new JLabel("Mappings (jsonKey=varName):"), c);

        c.gridx = 1;
        c.weightx = 1.0;
        JTextField mappingsField = new JTextField(30);
        mappingsField.setText(properties.getOrDefault(VARIABLE_MAPPINGS, "").toString());
        mappingsField.addActionListener(e -> properties.put(VARIABLE_MAPPINGS, mappingsField.getText()));
        mappingsField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                properties.put(VARIABLE_MAPPINGS, mappingsField.getText());
            }
        });
        p.add(mappingsField, c);

        return p;
    }

    @Override
    protected void writeAttributes(XMLWriter out, IdMap uid_map) {
        Graph.printAtt(out, VARIABLE_MAPPINGS, this.getProperty(VARIABLE_MAPPINGS).toString());

        Slot v = (Slot) this.getProperty(SOURCE_VAR);
        if (v != null) {
            try {
                String uid = uid_map.variables.getKey(v);
                Graph.printAtt(out, SOURCE_VAR, uid);
            } catch (Exception exn) {
                // Variable deleted
            }
        }
    }

    @Override
    protected void readAttribute(XMLReader r, String name, String value, IdMap uid_map) throws SAXException {
        if (name.equals(VARIABLE_MAPPINGS)) {
            this.setProperty(name, value);
        } else if (name.equals(SOURCE_VAR) && value != null) {
            try {
                this.setProperty(name, uid_map.variables.get(value));
            } catch (Exception exn) {
                this.setProperty(name, value);
            }
        } else {
            super.readAttribute(r, name, value, uid_map);
        }
    }
}
