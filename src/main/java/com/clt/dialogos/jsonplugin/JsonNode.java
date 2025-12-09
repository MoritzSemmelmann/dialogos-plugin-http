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
            
            String[] varNames = varNamesStr.split(",");
            
            JSONObject jsonObject = JsonConverter.variablesToJson(varNames, this::getSlot);
            
            // Pretty print JSON
            String jsonString = jsonObject.toString(2);
            System.out.println("\n Generated JSON");
            System.out.println(jsonString);
            
        } catch (NodeExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new NodeExecutionException(this, "Failed to build JSON: " + e.getMessage(), e);
        }
        
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
        JPanel mainPanel = new JPanel(new BorderLayout());
        
        JPanel varsPanel = new JPanel(new GridBagLayout());
    
        List<Slot> allVars = this.getGraph().getAllVariables(Graph.LOCAL);
        
        String varNamesStr = properties.getOrDefault(VARIABLE_NAMES, "").toString();
        
        if (!varNamesStr.trim().isEmpty()) {
            String[] parts = varNamesStr.split(",");
            int index = 0;
            for (String part : parts) {
                addVariableRowAt(varsPanel, properties, allVars, part.trim(), index++);
            }
        } else {
            addVariableRowAt(varsPanel, properties, allVars, "", 0);
        }
        
        JScrollPane scrollPane = new JScrollPane(varsPanel);
        scrollPane.setPreferredSize(new Dimension(400, 200));
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        
        return mainPanel;
    }
    
    private void updateVariableNames(JPanel varsPanel, Map<String, Object> properties) {
        List<String> selectedVars = new ArrayList<>();
        
        for (Component comp : varsPanel.getComponents()) {
            if (comp instanceof JPanel) {
                JPanel rowPanel = (JPanel) comp;
                for (Component rowComp : rowPanel.getComponents()) {
                    if (rowComp instanceof JComboBox) {
                        @SuppressWarnings("unchecked")
                        JComboBox<String> comboBox = (JComboBox<String>) rowComp;
                        String selected = (String) comboBox.getSelectedItem();
                        if (selected != null && !selected.isEmpty()) {
                            selectedVars.add(selected);
                        }
                    }
                }
            }
        }
        
        String varNamesStr = String.join(", ", selectedVars);
        properties.put(VARIABLE_NAMES, varNamesStr);
    }
    
    private int getRowIndex(JPanel varsPanel, JPanel rowPanel) {
        Component[] components = varsPanel.getComponents();
        for (int i = 0; i < components.length; i++) {
            if (components[i] == rowPanel) {
                return i;
            }
        }
        return -1;
    }
    
    private void addVariableRowAt(JPanel varsPanel, Map<String, Object> properties, List<Slot> allVars, String initialValue, int index) {
        JPanel rowPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 2, 2, 2);
        
        c.gridx = 0;
        c.weightx = 1.0;
        JComboBox<String> comboBox = new JComboBox<>();
        comboBox.addItem(""); 
        for (Slot slot : allVars) {
            comboBox.addItem(slot.getName());
        }
        
        if (!initialValue.isEmpty()) {
            comboBox.setSelectedItem(initialValue);
        }
        
        comboBox.addActionListener(e -> updateVariableNames(varsPanel, properties));
        rowPanel.add(comboBox, c);
        
        c.gridx = 1;
        c.weightx = 0;
        JButton plusButton = new JButton("+");
        plusButton.addActionListener(e -> {
            int idx = getRowIndex(varsPanel, rowPanel);
            addVariableRowAt(varsPanel, properties, allVars, "", idx + 1);
            varsPanel.revalidate();
            varsPanel.repaint();
            updateVariableNames(varsPanel, properties);
        });
        rowPanel.add(plusButton, c);
        
        c.gridx = 2;
        JButton minusButton = new JButton("-");
        minusButton.addActionListener(e -> {
            varsPanel.remove(rowPanel);
            varsPanel.revalidate();
            varsPanel.repaint();
            updateVariableNames(varsPanel, properties);
        });
        rowPanel.add(minusButton, c);
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = index;
        gbc.weightx = 1.0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(0, 0, 0, 0);
        
        varsPanel.add(rowPanel, gbc, index);
        
        GridBagConstraints fillerGbc = new GridBagConstraints();
        fillerGbc.gridx = 0;
        fillerGbc.gridy = 999;
        fillerGbc.weighty = 1.0;
        fillerGbc.fill = GridBagConstraints.BOTH;
        varsPanel.add(Box.createVerticalGlue(), fillerGbc);
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
