package com.clt.dialogos.jsonplugin;

import com.clt.diamant.*;
import com.clt.diamant.graph.Graph;
import com.clt.diamant.graph.Node;
import com.clt.diamant.graph.nodes.NodeExecutionException;
import com.clt.diamant.gui.NodePropertiesDialog;
import com.clt.script.exp.Value;
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

public class JsonNode extends Node {

    private static final String VARIABLE_NAMES = "variableNames";

    public JsonNode() {
        this.addEdge(); 
        this.setProperty(VARIABLE_NAMES, "");
    }

    public static String getNodeTypeName(Class<?> c) {
        return "JSON Builder";
    }

    @Override
    public Node execute(WozInterface comm, InputCenter input, ExecutionLogger logger) {
        logNode(logger);
        
        try {
            String varNamesStr = this.getProperty(VARIABLE_NAMES).toString().trim();
            
            if (varNamesStr.isEmpty()) {
                throw new NodeExecutionException(this, "No variables specified");
            }
            
            // Build JSON object from variables
            JSONObject jsonObject = new JSONObject();
            String[] varNames = varNamesStr.split(",");
            
            for (String varName : varNames) {
                varName = varName.trim();
                try {
                    Slot slot = getSlot(varName);
                    Value value = slot.getValue();
                    Object jsonValue = valueToJson(value);
                    jsonObject.put(varName, jsonValue);
                    
                    System.out.println("  " + varName + ": " + jsonValue + " (" + value.getType() + ")");
                    
                } catch (Exception e) {
                    System.out.println("  ERROR: " + varName + " - " + e.getMessage());
                    throw new NodeExecutionException(this, "Error reading variable: " + varName, e);
                }
            }
            
            // Pretty print JSON
            String jsonString = jsonObject.toString(2);
            System.out.println("\n--- Generated JSON ---");
            System.out.println(jsonString);
            
        } catch (NodeExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new NodeExecutionException(this, "Failed to build JSON: " + e.getMessage(), e);
        }
        
        return getEdge(0).getTarget();
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
        p.add(new JLabel("Variable names (comma-separated):"), c);

        c.gridx = 1;
        c.weightx = 1.0;
        JTextField textField = new JTextField(20);
        textField.setText(properties.getOrDefault(VARIABLE_NAMES, "").toString());
        textField.addActionListener(e -> properties.put(VARIABLE_NAMES, textField.getText()));
        textField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                properties.put(VARIABLE_NAMES, textField.getText());
            }
        });
        p.add(textField, c);

        return p;
    }

    @Override
    protected void writeAttributes(XMLWriter out, IdMap uid_map) {
        Graph.printAtt(out, VARIABLE_NAMES, this.getProperty(VARIABLE_NAMES).toString());
    }

    @Override
    protected void readAttribute(XMLReader r, String name, String value, IdMap uid_map) throws SAXException {
        if (name.equals(VARIABLE_NAMES)) {
            this.setProperty(name, value);
        } else {
            super.readAttribute(r, name, value, uid_map);
        }
    }
}
