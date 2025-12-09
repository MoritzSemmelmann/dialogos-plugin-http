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
            String url = this.getProperty(HTTP_URL).toString();
            String pathVarsStr = this.getProperty(PATH_VARIABLES).toString();
            String queryParamsStr = this.getProperty(QUERY_PARAMETERS).toString();
            String varNamesStr = this.getProperty(VARIABLE_NAMES).toString().trim();
            
            String[] pathVarMappings = pathVarsStr.isEmpty() ? new String[0] : pathVarsStr.split(",");
            String[] queryParamVars = queryParamsStr.isEmpty() ? new String[0] : queryParamsStr.split(",");
            String[] bodyVars = varNamesStr.isEmpty() ? new String[0] : varNamesStr.split(",");
            
            // Build JSON object from body variables
            JSONObject jsonBody = JsonConverter.variablesToJson(bodyVars, this::getSlot);
            
            System.out.println("\n Generated JSON Body");
            System.out.println(jsonBody.toString(2));

            // Send HTTP request with JSON object
            HttpHandler.sendHttpRequest(
                url,
                pathVarMappings,
                queryParamVars,
                jsonBody,
                this::getSlot
            );
            
        } catch (NodeExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new NodeExecutionException(this, "Failed to send HTTP request: " + e.getMessage(), e);
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
        
        JPanel urlContainer = new JPanel(new BorderLayout());
        urlContainer.add(urlField, BorderLayout.NORTH);

        JPanel pathVarsPanel = new JPanel(new GridBagLayout());
        JScrollPane pathScrollPane = new JScrollPane(pathVarsPanel);
        pathScrollPane.setPreferredSize(new Dimension(400, 100));
        pathScrollPane.setBorder(BorderFactory.createTitledBorder("Path Variables"));
        urlContainer.add(pathScrollPane, BorderLayout.CENTER);
        
        Runnable updatePathVars = () -> {
            properties.put(HTTP_URL, urlField.getText());
            pathVarsPanel.removeAll();
            List<String> pathVarNames = extractPathVariables(urlField.getText());
            List<Slot> allVars = this.getGraph().getAllVariables(Graph.LOCAL);
            
            String savedMappings = properties.getOrDefault(PATH_VARIABLES, "").toString();
            Map<String, String> mappingMap = parseMappings(savedMappings);
            
            int index = 0;
            for (String pathVarName : pathVarNames) {
                addPathVariableRow(pathVarsPanel, properties, allVars, pathVarName, 
                                  mappingMap.getOrDefault(pathVarName, ""), index++);
            }
            
            pathVarsPanel.revalidate();
            pathVarsPanel.repaint();
        };
        
        urlField.addActionListener(e -> updatePathVars.run());
        urlField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                updatePathVars.run();
            }
        });
        
        updatePathVars.run();
        
        mainPanel.add(urlContainer, gbc);
        

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 0;
        mainPanel.add(new JLabel("Query Parameters:"), gbc);
        
        gbc.gridy = 2;
        gbc.weighty = 0.5;
        JPanel queryParamsPanel = createVariablePanel(properties, QUERY_PARAMETERS);
        JScrollPane queryScrollPane = new JScrollPane(queryParamsPanel);
        queryScrollPane.setPreferredSize(new Dimension(400, 100));
        mainPanel.add(queryScrollPane, gbc);
        
        gbc.gridy = 3;
        gbc.weighty = 0;
        mainPanel.add(new JLabel("JSON Body Variables:"), gbc);
        
        gbc.gridy = 4;
        gbc.weighty = 0.5;
        JPanel varsPanel = createVariablePanel(properties, VARIABLE_NAMES);
        JScrollPane scrollPane = new JScrollPane(varsPanel);
        scrollPane.setPreferredSize(new Dimension(400, 100));
        mainPanel.add(scrollPane, gbc);
        
        return mainPanel;
    }
    
    private List<String> extractPathVariables(String url) {
        List<String> pathVars = new ArrayList<>();
        int start = 0;
        while ((start = url.indexOf('{', start)) != -1) {
            int end = url.indexOf('}', start);
            if (end != -1) {
                String varName = url.substring(start + 1, end);
                pathVars.add(varName);
                start = end + 1;
            } else {
                break;
            }
        }
        return pathVars;
    }
    
    private Map<String, String> parseMappings(String mappingsStr) {
        Map<String, String> mappings = new java.util.HashMap<>();
        if (mappingsStr.trim().isEmpty()) {
            return mappings;
        }
        
        String[] parts = mappingsStr.split(",");
        for (String part : parts) {
            part = part.trim();
            String[] keyValue = part.split("=");
            if (keyValue.length == 2) {
                mappings.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }
        return mappings;
    }
    
    private void addPathVariableRow(JPanel panel, Map<String, Object> properties, 
                                    List<Slot> allVars, String pathVarName, 
                                    String selectedVar, int index) {
        JPanel rowPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 2, 2, 2);
        
        c.gridx = 0;
        c.weightx = 0.3;
        JLabel label = new JLabel("{" + pathVarName + "} =");
        rowPanel.add(label, c);
        
        c.gridx = 1;
        c.weightx = 0.7;
        JComboBox<String> comboBox = new JComboBox<>();
        comboBox.addItem("");
        for (Slot slot : allVars) {
            comboBox.addItem(slot.getName());
        }
        
        if (!selectedVar.isEmpty()) {
            comboBox.setSelectedItem(selectedVar);
        }
        
        comboBox.addActionListener(e -> updatePathVariableMappings(panel, properties));
        rowPanel.add(comboBox, c);
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = index;
        gbc.weightx = 1.0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        
        panel.add(rowPanel, gbc, index);
        
        GridBagConstraints fillerGbc = new GridBagConstraints();
        fillerGbc.gridx = 0;
        fillerGbc.gridy = 999;
        fillerGbc.weighty = 1.0;
        fillerGbc.fill = GridBagConstraints.BOTH;
        panel.add(Box.createVerticalGlue(), fillerGbc);
    }
    
    private void updatePathVariableMappings(JPanel panel, Map<String, Object> properties) {
        List<String> mappings = new ArrayList<>();
        
        for (Component comp : panel.getComponents()) {
            if (comp instanceof JPanel) {
                JPanel rowPanel = (JPanel) comp;
                String pathVarName = null;
                String selectedVar = null;
                
                for (Component rowComp : rowPanel.getComponents()) {
                    if (rowComp instanceof JLabel) {
                        JLabel label = (JLabel) rowComp;
                        String text = label.getText();
                        if (text.startsWith("{") && text.contains("}")) {
                            pathVarName = text.substring(1, text.indexOf("}"));
                        }
                    } else if (rowComp instanceof JComboBox) {
                        @SuppressWarnings("unchecked")
                        JComboBox<String> comboBox = (JComboBox<String>) rowComp;
                        selectedVar = (String) comboBox.getSelectedItem();
                    }
                }
                
                if (pathVarName != null && selectedVar != null && !selectedVar.isEmpty()) {
                    mappings.add(pathVarName + "=" + selectedVar);
                }
            }
        }
        
        properties.put(PATH_VARIABLES, String.join(", ", mappings));
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
