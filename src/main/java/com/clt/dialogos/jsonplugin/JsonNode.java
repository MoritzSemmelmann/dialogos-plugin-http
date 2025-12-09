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
    private static final String HTTP_URL = "httpUrl";
    private static final String PATH_VARIABLES = "pathVariables";
    private static final String QUERY_PARAMETERS = "queryParameters";

    public JsonNode() {
        this.addEdge(); 
        this.setProperty(VARIABLE_NAMES, "");
        this.setProperty(HTTP_URL, "");
        this.setProperty(PATH_VARIABLES, "");
        this.setProperty(QUERY_PARAMETERS, "");
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
            
            String jsonString = jsonObject.toString(2);
            System.out.println("\n Generated JSON");
            System.out.println(jsonString);

            HttpHandler.sendHttpRequest(
                this.getProperty(HTTP_URL).toString(),
                this.getProperty(PATH_VARIABLES).toString(),
                this.getProperty(QUERY_PARAMETERS).toString(),
                jsonString
            );
            
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
        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 5);
        
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        gbc.weighty = 0;
        mainPanel.add(new JLabel("HTTP URL:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JTextField urlField = new JTextField(20);
        urlField.setText(properties.getOrDefault(HTTP_URL, "").toString());
        urlField.addActionListener(e -> properties.put(HTTP_URL, urlField.getText()));
        urlField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                properties.put(HTTP_URL, urlField.getText());
            }
        });
        mainPanel.add(urlField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 0;
        mainPanel.add(new JLabel("Path Variables:"), gbc);
        
        gbc.gridy = 2;
        gbc.weighty = 0.33;
        JPanel pathVarsPanel = createVariablePanel(properties, PATH_VARIABLES);
        JScrollPane pathScrollPane = new JScrollPane(pathVarsPanel);
        pathScrollPane.setPreferredSize(new Dimension(400, 100));
        mainPanel.add(pathScrollPane, gbc);
        
        gbc.gridy = 3;
        gbc.weighty = 0;
        mainPanel.add(new JLabel("Query Parameters:"), gbc);
        
        gbc.gridy = 4;
        gbc.weighty = 0.33;
        JPanel queryParamsPanel = createVariablePanel(properties, QUERY_PARAMETERS);
        JScrollPane queryScrollPane = new JScrollPane(queryParamsPanel);
        queryScrollPane.setPreferredSize(new Dimension(400, 100));
        mainPanel.add(queryScrollPane, gbc);
        
        gbc.gridy = 5;
        gbc.weighty = 0;
        mainPanel.add(new JLabel("JSON Body Variables:"), gbc);
        
        gbc.gridy = 6;
        gbc.weighty = 0.34;
        JPanel varsPanel = createVariablePanel(properties, VARIABLE_NAMES);
        JScrollPane scrollPane = new JScrollPane(varsPanel);
        scrollPane.setPreferredSize(new Dimension(400, 100));
        mainPanel.add(scrollPane, gbc);
        
        return mainPanel;
    }
    
    private JPanel createVariablePanel(Map<String, Object> properties, String propertyKey) {
        JPanel varsPanel = new JPanel(new GridBagLayout());
        List<Slot> allVars = this.getGraph().getAllVariables(Graph.LOCAL);
        
        String varNamesStr = properties.getOrDefault(propertyKey, "").toString();
        
        if (!varNamesStr.trim().isEmpty()) {
            String[] parts = varNamesStr.split(",");
            int index = 0;
            for (String part : parts) {
                addVariableRowAt(varsPanel, properties, allVars, part.trim(), index++, propertyKey);
            }
        } else {
            addVariableRowAt(varsPanel, properties, allVars, "", 0, propertyKey);
        }
        
        return varsPanel;
    }
    
    private void updateVariableNames(JPanel varsPanel, Map<String, Object> properties, String propertyKey) {
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
        properties.put(propertyKey, varNamesStr);
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
    
    private void addVariableRowAt(JPanel varsPanel, Map<String, Object> properties, List<Slot> allVars, String initialValue, int index, String propertyKey) {
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
        
        comboBox.addActionListener(e -> updateVariableNames(varsPanel, properties, propertyKey));
        rowPanel.add(comboBox, c);
        
        c.gridx = 1;
        c.weightx = 0;
        JButton plusButton = new JButton("+");
        plusButton.addActionListener(e -> {
            int idx = getRowIndex(varsPanel, rowPanel);
            addVariableRowAt(varsPanel, properties, allVars, "", idx + 1, propertyKey);
            varsPanel.revalidate();
            varsPanel.repaint();
            updateVariableNames(varsPanel, properties, propertyKey);
        });
        rowPanel.add(plusButton, c);
        
        c.gridx = 2;
        JButton minusButton = new JButton("-");
        minusButton.addActionListener(e -> {
            varsPanel.remove(rowPanel);
            varsPanel.revalidate();
            varsPanel.repaint();
            updateVariableNames(varsPanel, properties, propertyKey);
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
        Graph.printAtt(out, HTTP_URL, this.getProperty(HTTP_URL).toString());
        Graph.printAtt(out, PATH_VARIABLES, this.getProperty(PATH_VARIABLES).toString());
        Graph.printAtt(out, QUERY_PARAMETERS, this.getProperty(QUERY_PARAMETERS).toString());
    }

    @Override
    protected void readAttribute(XMLReader r, String name, String value, IdMap uid_map) throws SAXException {
        if (name.equals(VARIABLE_NAMES) || name.equals(HTTP_URL) || 
            name.equals(PATH_VARIABLES) || name.equals(QUERY_PARAMETERS)) {
            this.setProperty(name, value);
        } else {
            super.readAttribute(r, name, value, uid_map);
        }
    }
}
