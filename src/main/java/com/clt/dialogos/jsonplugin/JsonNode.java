package com.clt.dialogos.jsonplugin;

import com.clt.diamant.*;
import com.clt.diamant.graph.Graph;
import com.clt.diamant.graph.Node;
import com.clt.diamant.graph.nodes.NodeExecutionException;
import com.clt.diamant.gui.NodePropertiesDialog;
import com.clt.script.exp.Value;
import com.clt.xml.XMLReader;
import com.clt.xml.XMLWriter;
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
        return "JSON Test Node";
    }

    @Override
    public Node execute(WozInterface comm, InputCenter input, ExecutionLogger logger) {
        logNode(logger);

        String varNamesStr = this.getProperty(VARIABLE_NAMES).toString().trim();
        if (!varNamesStr.isEmpty()) {
            String[] varNames = varNamesStr.split(",");
            for (String varName : varNames) {
                varName = varName.trim();
                try {
                    Slot slot = getSlot(varName);
                    Value value = slot.getValue();
                    System.out.println("  " + varName + " = " + value);
                    System.out.println("    Type: " + value.getType());
                    
                } catch (Exception e) {
                    System.out.println("  ERROR reading " + varName + ": " + e.getMessage());
                }
            }
        }
        
        System.out.println("========================\n");
        
        return getEdge(0).getTarget();
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
