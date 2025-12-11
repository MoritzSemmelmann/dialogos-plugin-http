package com.clt.dialogos.jsonplugin;

import com.clt.diamant.*;
import com.clt.diamant.graph.Graph;
import com.clt.diamant.graph.Node;
import com.clt.diamant.graph.nodes.NodeExecutionException;
import com.clt.script.exp.Value;
import com.clt.xml.XMLReader;
import com.clt.xml.XMLWriter;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SendAndReceiveNode extends Node {
    private static final String URL = "url";
    private static final String HTTP_METHOD = "httpMethod";
    private static final String PATH_VARIABLES = "pathVariables";
    private static final String QUERY_VARIABLES = "queryVariables";
    private static final String BODY_VARIABLES = "bodyVariables";
    private static final String RESPONSE_MAPPINGS = "responseMappings";

    public SendAndReceiveNode() {
        this.addEdge();
        this.setProperty(URL, "");
        this.setProperty(HTTP_METHOD, "GET");
        this.setProperty(PATH_VARIABLES, "");
        this.setProperty(QUERY_VARIABLES, "");
        this.setProperty(BODY_VARIABLES, "");
        this.setProperty(RESPONSE_MAPPINGS, "");
    }

    public static String getNodeTypeName(Class<?> c) {
        return "Send and Receive JSON";
    }

    @Override
    public Node execute(WozInterface comm, InputCenter input, ExecutionLogger logger) {
        logNode(logger);
        try {
            // Build JSON body
            String bodyVarsStr = this.getProperty(BODY_VARIABLES).toString().trim();
            String[] bodyVars = bodyVarsStr.isEmpty() ? new String[0] : bodyVarsStr.split(",");
            JSONObject jsonBody = JsonConverter.variablesToJson(bodyVars, this::getSlot);

            // Get URL, method, path/query variables
            String url = this.getProperty(URL).toString().trim();
            String httpMethod = this.getProperty(HTTP_METHOD).toString().trim();
            String pathVarsStr = this.getProperty(PATH_VARIABLES).toString().trim();
            String[] pathVars = pathVarsStr.isEmpty() ? new String[0] : pathVarsStr.split(",");
            String queryVarsStr = this.getProperty(QUERY_VARIABLES).toString().trim();
            String[] queryVars = queryVarsStr.isEmpty() ? new String[0] : queryVarsStr.split(",");

            // Send HTTP request and get response
            String response = HttpHandler.sendHttpRequest(url, httpMethod, pathVars, queryVars, jsonBody, this::getSlot);
            JSONObject responseJson = new JSONObject(response);

            // Response mapping (jsonKey=varName, comma separated)
            String mappingsStr = this.getProperty(RESPONSE_MAPPINGS).toString().trim();
            if (mappingsStr.isEmpty()) {
                System.out.println("Warning: No response variable mappings specified");
                return getEdge(0).getTarget();
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
                        Value dialogosValue = JsonConverter.jsonToValue(jsonValue);
                        Slot targetSlot = getSlot(varName);
                        targetSlot.setValue(dialogosValue);
                        System.out.println("  " + jsonKey + " -> " + varName + ": " + dialogosValue + " (" + dialogosValue.getType() + ")");
                    } else {
                        System.out.println("  " + jsonKey + " not found in response JSON (skipping " + varName + ")");
                    }
                } catch (Exception e) {
                    System.out.println("  ERROR: " + jsonKey + " -> " + varName + ": " + e.getMessage());
                }
            }
        } catch (NodeExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new NodeExecutionException(this, "Failed to send/receive JSON: " + e.getMessage(), e);
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
        
        // HTTP Method dropdown
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(new JLabel("HTTP Method:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        String[] httpMethods = {"GET", "POST", "PUT", "DELETE", "PATCH"};
        JComboBox<String> methodComboBox = new JComboBox<>(httpMethods);
        methodComboBox.setSelectedItem(properties.getOrDefault(HTTP_METHOD, "GET"));
        methodComboBox.addActionListener(e -> properties.put(HTTP_METHOD, methodComboBox.getSelectedItem()));
        mainPanel.add(methodComboBox, gbc);
        
        // HTTP URL field
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.BOTH;
        mainPanel.add(new JLabel("HTTP URL:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JTextField urlField = new JTextField(20);
        urlField.setText(properties.getOrDefault(URL, "").toString());
        
        JPanel urlContainer = new JPanel(new BorderLayout());
        urlContainer.add(urlField, BorderLayout.NORTH);

        JPanel pathVarsPanel = new JPanel(new GridBagLayout());
        JScrollPane pathScrollPane = new JScrollPane(pathVarsPanel);
        pathScrollPane.setPreferredSize(new Dimension(400, 100));
        pathScrollPane.setBorder(BorderFactory.createTitledBorder("Path Variables"));
        urlContainer.add(pathScrollPane, BorderLayout.CENTER);
        
        Runnable updatePathVars = () -> {
            properties.put(URL, urlField.getText());
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
        
        // Query Parameters
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 0;
        mainPanel.add(new JLabel("Query Parameters:"), gbc);
        
        gbc.gridy = 3;
        gbc.weighty = 0.3;
        JPanel queryParamsPanel = createVariablePanel(properties, QUERY_VARIABLES);
        JScrollPane queryScrollPane = new JScrollPane(queryParamsPanel);
        queryScrollPane.setPreferredSize(new Dimension(400, 80));
        mainPanel.add(queryScrollPane, gbc);
        
        // JSON Body Variables
        gbc.gridy = 4;
        gbc.weighty = 0;
        mainPanel.add(new JLabel("JSON Body Variables:"), gbc);
        
        gbc.gridy = 5;
        gbc.weighty = 0.3;
        JPanel varsPanel = createVariablePanel(properties, BODY_VARIABLES);
        JScrollPane scrollPane = new JScrollPane(varsPanel);
        scrollPane.setPreferredSize(new Dimension(400, 80));
        mainPanel.add(scrollPane, gbc);
        
        // Response Mappings
        gbc.gridy = 6;
        gbc.weighty = 0;
        mainPanel.add(new JLabel("Response Mappings:"), gbc);
        
        gbc.gridy = 7;
        gbc.weighty = 0.3;
        JPanel responseMappingsPanel = createResponseMappingPanel(properties);
        JScrollPane responseMappingsScrollPane = new JScrollPane(responseMappingsPanel);
        responseMappingsScrollPane.setPreferredSize(new Dimension(400, 80));
        mainPanel.add(responseMappingsScrollPane, gbc);
        
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

    private JPanel createResponseMappingPanel(Map<String, Object> properties) {
        JPanel mappingsPanel = new JPanel(new GridBagLayout());
        List<Slot> allVars = this.getGraph().getAllVariables(Graph.LOCAL);
        
        String mappingsStr = properties.getOrDefault(RESPONSE_MAPPINGS, "").toString();
        
        if (!mappingsStr.trim().isEmpty()) {
            String[] mappings = mappingsStr.split(",");
            int index = 0;
            for (String mapping : mappings) {
                mapping = mapping.trim();
                if (!mapping.isEmpty()) {
                    String[] parts = mapping.split("=");
                    String jsonKey = parts.length > 0 ? parts[0].trim() : "";
                    String varName = parts.length > 1 ? parts[1].trim() : "";
                    addResponseMappingRowAt(mappingsPanel, properties, allVars, jsonKey, varName, index++);
                }
            }
        } else {
            addResponseMappingRowAt(mappingsPanel, properties, allVars, "", "", 0);
        }
        
        return mappingsPanel;
    }
    
    private void addResponseMappingRowAt(JPanel mappingsPanel, Map<String, Object> properties, 
                                         List<Slot> allVars, String jsonKey, String varName, int index) {
        JPanel rowPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 2, 2, 2);
        
        // JSON Key TextField
        c.gridx = 0;
        c.weightx = 0.4;
        JTextField jsonKeyField = new JTextField(jsonKey, 10);
        jsonKeyField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                updateResponseMappings(mappingsPanel, properties);
            }
        });
        rowPanel.add(jsonKeyField, c);
        
        // "=" Label
        c.gridx = 1;
        c.weightx = 0;
        rowPanel.add(new JLabel(" = "), c);
        
        // Variable Dropdown
        c.gridx = 2;
        c.weightx = 0.4;
        JComboBox<String> comboBox = new JComboBox<>();
        comboBox.addItem("");
        for (Slot slot : allVars) {
            comboBox.addItem(slot.getName());
        }
        
        if (!varName.isEmpty()) {
            comboBox.setSelectedItem(varName);
        }
        
        comboBox.addActionListener(e -> updateResponseMappings(mappingsPanel, properties));
        rowPanel.add(comboBox, c);
        
        // Plus Button
        c.gridx = 3;
        c.weightx = 0;
        JButton plusButton = new JButton("+");
        plusButton.addActionListener(e -> {
            int idx = getRowIndex(mappingsPanel, rowPanel);
            addResponseMappingRowAt(mappingsPanel, properties, allVars, "", "", idx + 1);
            mappingsPanel.revalidate();
            mappingsPanel.repaint();
            updateResponseMappings(mappingsPanel, properties);
        });
        rowPanel.add(plusButton, c);
        
        // Minus Button
        c.gridx = 4;
        JButton minusButton = new JButton("-");
        minusButton.addActionListener(e -> {
            mappingsPanel.remove(rowPanel);
            mappingsPanel.revalidate();
            mappingsPanel.repaint();
            updateResponseMappings(mappingsPanel, properties);
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
        
        mappingsPanel.add(rowPanel, gbc, index);
        
        GridBagConstraints fillerGbc = new GridBagConstraints();
        fillerGbc.gridx = 0;
        fillerGbc.gridy = 999;
        fillerGbc.weighty = 1.0;
        fillerGbc.fill = GridBagConstraints.BOTH;
        mappingsPanel.add(Box.createVerticalGlue(), fillerGbc);
    }
    
    private void updateResponseMappings(JPanel mappingsPanel, Map<String, Object> properties) {
        List<String> mappings = new ArrayList<>();
        
        for (Component comp : mappingsPanel.getComponents()) {
            if (comp instanceof JPanel) {
                JPanel rowPanel = (JPanel) comp;
                String jsonKey = null;
                String varName = null;
                
                for (Component rowComp : rowPanel.getComponents()) {
                    if (rowComp instanceof JTextField) {
                        jsonKey = ((JTextField) rowComp).getText().trim();
                    } else if (rowComp instanceof JComboBox) {
                        @SuppressWarnings("unchecked")
                        JComboBox<String> comboBox = (JComboBox<String>) rowComp;
                        varName = (String) comboBox.getSelectedItem();
                    }
                }
                
                if (jsonKey != null && !jsonKey.isEmpty() && varName != null && !varName.isEmpty()) {
                    mappings.add(jsonKey + "=" + varName);
                }
            }
        }
        
        properties.put(RESPONSE_MAPPINGS, String.join(", ", mappings));
    }

    @Override
    protected void writeAttributes(XMLWriter out, IdMap uid_map) {
        Graph.printAtt(out, URL, this.getProperty(URL).toString());
        Graph.printAtt(out, HTTP_METHOD, this.getProperty(HTTP_METHOD).toString());
        Graph.printAtt(out, PATH_VARIABLES, this.getProperty(PATH_VARIABLES).toString());
        Graph.printAtt(out, QUERY_VARIABLES, this.getProperty(QUERY_VARIABLES).toString());
        Graph.printAtt(out, BODY_VARIABLES, this.getProperty(BODY_VARIABLES).toString());
        Graph.printAtt(out, RESPONSE_MAPPINGS, this.getProperty(RESPONSE_MAPPINGS).toString());
    }

    @Override
    protected void readAttribute(XMLReader r, String name, String value, IdMap uid_map) throws SAXException {
        if (name.equals(URL) || name.equals(HTTP_METHOD) || name.equals(PATH_VARIABLES) ||
            name.equals(QUERY_VARIABLES) || name.equals(BODY_VARIABLES) || name.equals(RESPONSE_MAPPINGS)) {
            this.setProperty(name, value);
        } else {
            super.readAttribute(r, name, value, uid_map);
        }
    }
}
