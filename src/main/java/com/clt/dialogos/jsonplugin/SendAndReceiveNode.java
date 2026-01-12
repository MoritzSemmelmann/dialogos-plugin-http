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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SendAndReceiveNode extends Node {
    private static final String URL = "url";
    private static final String HTTP_METHOD = "httpMethod";
    private static final String PATH_VARIABLES = "pathVariables";
    private static final String QUERY_VARIABLES = "queryVariables";
    private static final String BODY_VARIABLES = "bodyVariables";
    private static final String RESPONSE_MODE = "responseMode"; 
    private static final String RESPONSE_MAPPINGS = "responseMappings";
    private static final String RESPONSE_TARGET_VAR = "responseTargetVar";
    private static final String RESPONSE_AS_STRING = "responseAsString";
    private static final String AUTH_TYPE = "authType";
    private static final String AUTH_VALUE = "authValue";
    private static final String CUSTOM_HEADERS = "customHeaders";

    public SendAndReceiveNode() {
        this.addEdge("Success");
        this.addEdge("Error");
        
        this.getEdge(0).setColor(new Color(0, 150, 0));
        this.getEdge(1).setColor(new Color(200, 0, 0));
        
        this.setProperty(URL, "");
        this.setProperty(HTTP_METHOD, "GET");
        this.setProperty(PATH_VARIABLES, "");
        this.setProperty(QUERY_VARIABLES, "");
        this.setProperty(BODY_VARIABLES, "");
        this.setProperty(RESPONSE_MODE, "multiple");
        this.setProperty(RESPONSE_MAPPINGS, "");
        this.setProperty(RESPONSE_TARGET_VAR, "");
        this.setProperty(RESPONSE_AS_STRING, "false");
        this.setProperty(AUTH_TYPE, "None");
        this.setProperty(AUTH_VALUE, "");
        this.setProperty(CUSTOM_HEADERS, "");
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
            Map<String, String> bodyVarMappings = parseMappingsToMap(bodyVarsStr);
            JSONObject jsonBody = JsonConverter.variablesToJson(bodyVarMappings, this::getSlotOrNull);

            // Get URL, method, path/query variables
            String url = this.getProperty(URL).toString().trim();
            String httpMethod = this.getProperty(HTTP_METHOD).toString().trim();
            String pathVarsStr = this.getProperty(PATH_VARIABLES).toString().trim();
            String[] pathVars = pathVarsStr.isEmpty() ? new String[0] : pathVarsStr.split(",");
            String queryVarsStr = this.getProperty(QUERY_VARIABLES).toString().trim();
            Map<String, String> queryVarMappings = parseMappingsToMap(queryVarsStr);

            String authType = this.getProperty(AUTH_TYPE).toString();
            String authValue = this.getProperty(AUTH_VALUE).toString();
            String customHeaders = this.getProperty(CUSTOM_HEADERS).toString();
            
            // Send HTTP request and get response
            HttpHandler.HttpResult result = HttpHandler.sendHttpRequest(url, httpMethod, pathVars, queryVarMappings, jsonBody, this::getSlotOrNull, authType, authValue, customHeaders);
            
            if (!result.success) {
                System.err.println("HTTP request failed: " + result.errorMessage);
                return getEdge(1).getTarget();
            }
            
            JSONObject responseJson = new JSONObject(result.response);

            String responseMode = this.getProperty(RESPONSE_MODE).toString();
            if ("single".equals(responseMode)) {
                // Single variable mode
                String targetVar = this.getProperty(RESPONSE_TARGET_VAR).toString().trim();
                boolean asString = Boolean.parseBoolean(this.getProperty(RESPONSE_AS_STRING).toString());
                JsonConverter.mapJsonToSingleVariable(responseJson, targetVar, asString, this::getSlot);
            } else {
                // Multiple variables mode (default)
                String mappingsStr = this.getProperty(RESPONSE_MAPPINGS).toString().trim();
                JsonConverter.mapJsonToVariables(responseJson, mappingsStr, this::getSlot);
            }
            
            return getEdge(0).getTarget();
            
        } catch (Exception e) {
            System.err.println("Error in SendAndReceiveNode: " + e.getMessage());
            return getEdge(1).getTarget();
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
    
    private Slot getSlotOrNull(String name) {
        List<Slot> slots = this.getGraph().getAllVariables(Graph.LOCAL);
        for (Slot slot : slots) {
            if (name.equals(slot.getName()))
                return slot;
        }
        return null;
    }
    
    private Map<String, String> parseMappingsToMap(String mappingsStr) {
        Map<String, String> result = new LinkedHashMap<>();
        
        if (mappingsStr == null || mappingsStr.trim().isEmpty()) {
            return result;
        }
        
        String[] parts = mappingsStr.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;
            
            if (part.contains("=")) {
                String[] mapping = part.split("=", 2);
                String key = mapping[0].trim();
                String varName = mapping[1].trim();
                result.put(key, varName);
            } else {
                // No key specified, use variable name as key
                result.put(part, part);
            }
        }
        
        return result;
    }

    @Override
    public JComponent createEditorComponent(Map<String, Object> properties) {
        JTabbedPane tabs = new JTabbedPane();
        
        tabs.addTab("Send", createSendPanel(properties));
        
        tabs.addTab("Receive", createReceivePanel(properties));
        
        return tabs;
    }
    
    private JPanel createSendPanel(Map<String, Object> properties) {
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
        
        final List<String>[] lastPathVars = new List[]{new ArrayList<>()};
        
        Runnable updatePathVars = () -> {
            properties.put(URL, urlField.getText());
            List<String> pathVarNames = extractPathVariables(urlField.getText());
            
            if (!pathVarNames.equals(lastPathVars[0])) {
                lastPathVars[0] = new ArrayList<>(pathVarNames);
                
                pathVarsPanel.removeAll();
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
            }
        };
        
        urlField.addActionListener(e -> updatePathVars.run());
        urlField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                updatePathVars.run();
            }
        });
        
        // DocumentListener for dynamic updates while typing
        urlField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updatePathVars.run();
            }
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updatePathVars.run();
            }
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
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
        JPanel queryParamsPanel = createMappingPanel(properties, QUERY_VARIABLES);
        JScrollPane queryScrollPane = new JScrollPane(queryParamsPanel);
        queryScrollPane.setPreferredSize(new Dimension(400, 80));
        mainPanel.add(queryScrollPane, gbc);
        
        // Authorization Section
        gbc.gridy = 4;
        gbc.weighty = 0;
        mainPanel.add(new JLabel("Authorization:"), gbc);
        
        gbc.gridy = 5;
        gbc.weighty = 0;
        mainPanel.add(createAuthorizationPanel(properties), gbc);
        
        // Custom Headers
        gbc.gridy = 6;
        gbc.weighty = 0;
        mainPanel.add(new JLabel("Custom Headers:"), gbc);
        
        gbc.gridy = 7;
        gbc.weighty = 0.2;
        JPanel headersPanel = createHeadersPanel(properties);
        JScrollPane headersScrollPane = new JScrollPane(headersPanel);
        headersScrollPane.setPreferredSize(new Dimension(400, 60));
        mainPanel.add(headersScrollPane, gbc);
        
        // JSON Body Variables
        gbc.gridy = 8;
        gbc.weighty = 0;
        mainPanel.add(new JLabel("JSON Body Variables:"), gbc);
        
        gbc.gridy = 9;
        gbc.weighty = 0.3;
        JPanel varsPanel = createMappingPanel(properties, BODY_VARIABLES);
        JScrollPane scrollPane = new JScrollPane(varsPanel);
        scrollPane.setPreferredSize(new Dimension(400, 80));
        mainPanel.add(scrollPane, gbc);
        
        return mainPanel;
    }
    
    private JPanel createReceivePanel(Map<String, Object> properties) {
        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridwidth = 2;
        
        // Response Mode Selection
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 0;
        mainPanel.add(new JLabel("Response Mode:"), gbc);
        
        gbc.gridy = 1;
        gbc.weighty = 0;
        JPanel responseModePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        ButtonGroup modeGroup = new ButtonGroup();
        JRadioButton multipleVarsRadio = new JRadioButton("Map to multiple variables");
        JRadioButton singleVarRadio = new JRadioButton("Store in single variable");
        modeGroup.add(multipleVarsRadio);
        modeGroup.add(singleVarRadio);
        responseModePanel.add(multipleVarsRadio);
        responseModePanel.add(singleVarRadio);
        
        String currentMode = properties.getOrDefault(RESPONSE_MODE, "multiple").toString();
        if ("single".equals(currentMode)) {
            singleVarRadio.setSelected(true);
        } else {
            multipleVarsRadio.setSelected(true);
        }
        
        mainPanel.add(responseModePanel, gbc);
        
        // response configuration
        gbc.gridy = 2;
        gbc.weighty = 1.0;
        JPanel responseConfigContainer = new JPanel(new CardLayout());
        
        // Multiple variables panel
        JPanel multipleMappingsPanel = createResponseMappingPanel(properties);
        JScrollPane multipleMappingsScrollPane = new JScrollPane(multipleMappingsPanel);
        multipleMappingsScrollPane.setPreferredSize(new Dimension(400, 80));
        responseConfigContainer.add(multipleMappingsScrollPane, "multiple");
        
        // Single variable panel
        JPanel singleVarPanel = new JPanel(new GridBagLayout());
        GridBagConstraints svc = new GridBagConstraints();
        svc.fill = GridBagConstraints.HORIZONTAL;
        svc.insets = new Insets(5, 5, 5, 5);
        
        svc.gridx = 0;
        svc.gridy = 0;
        svc.weightx = 0;
        singleVarPanel.add(new JLabel("Target Variable:"), svc);
        
        svc.gridx = 1;
        svc.weightx = 1.0;
        JComboBox<String> targetVarCombo = new JComboBox<>();
        targetVarCombo.addItem("");
        List<Slot> allVars = this.getGraph().getAllVariables(Graph.LOCAL);
        for (Slot slot : allVars) {
            targetVarCombo.addItem(slot.getName());
        }
        targetVarCombo.setSelectedItem(properties.getOrDefault(RESPONSE_TARGET_VAR, "").toString());
        targetVarCombo.addActionListener(e -> properties.put(RESPONSE_TARGET_VAR, targetVarCombo.getSelectedItem()));
        singleVarPanel.add(targetVarCombo, svc);
        
        svc.gridx = 0;
        svc.gridy = 1;
        svc.weightx = 0;
        singleVarPanel.add(new JLabel("Store as:"), svc);
        
        svc.gridx = 1;
        svc.weightx = 1.0;
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        ButtonGroup typeGroup = new ButtonGroup();
        JRadioButton structRadio = new JRadioButton("Struct");
        JRadioButton stringRadio = new JRadioButton("String");
        typeGroup.add(structRadio);
        typeGroup.add(stringRadio);
        typePanel.add(structRadio);
        typePanel.add(stringRadio);
        
        boolean asString = Boolean.parseBoolean(properties.getOrDefault(RESPONSE_AS_STRING, "false").toString());
        if (asString) {
            stringRadio.setSelected(true);
        } else {
            structRadio.setSelected(true);
        }
        
        structRadio.addActionListener(e -> properties.put(RESPONSE_AS_STRING, "false"));
        stringRadio.addActionListener(e -> properties.put(RESPONSE_AS_STRING, "true"));
        
        singleVarPanel.add(typePanel, svc);
        
        responseConfigContainer.add(singleVarPanel, "single");
        
        CardLayout cardLayout = (CardLayout) responseConfigContainer.getLayout();
        cardLayout.show(responseConfigContainer, currentMode);
        
        // listeners to switch panels
        multipleVarsRadio.addActionListener(e -> {
            properties.put(RESPONSE_MODE, "multiple");
            cardLayout.show(responseConfigContainer, "multiple");
        });
        singleVarRadio.addActionListener(e -> {
            properties.put(RESPONSE_MODE, "single");
            cardLayout.show(responseConfigContainer, "single");
        });
        
        mainPanel.add(responseConfigContainer, gbc);
        
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
        comboBox.setEditable(true);
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

    private JPanel createMappingPanel(Map<String, Object> properties, String propertyKey) {
        JPanel varsPanel = new JPanel(new GridBagLayout());
        List<Slot> allVars = this.getGraph().getAllVariables(Graph.LOCAL);
        
        // Add header row
        JPanel headerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints hc = new GridBagConstraints();
        hc.fill = GridBagConstraints.HORIZONTAL;
        hc.insets = new Insets(2, 2, 2, 2);
        
        hc.gridx = 0;
        hc.weightx = 0.4;
        String keyLabelText = propertyKey.equals(QUERY_VARIABLES) ? "ParamKey" : "JsonKey";
        JLabel jsonKeyLabel = new JLabel(keyLabelText);
        jsonKeyLabel.setFont(jsonKeyLabel.getFont().deriveFont(java.awt.Font.BOLD));
        headerPanel.add(jsonKeyLabel, hc);
        
        hc.gridx = 1;
        hc.weightx = 0.6;
        JLabel variableLabel = new JLabel("Variable");
        variableLabel.setFont(variableLabel.getFont().deriveFont(java.awt.Font.BOLD));
        headerPanel.add(variableLabel, hc);
        
        hc.gridx = 2;
        hc.weightx = 0;
        headerPanel.add(new JLabel(""), hc);
        
        hc.gridx = 3;
        headerPanel.add(new JLabel(""), hc);
        
        GridBagConstraints headerGbc = new GridBagConstraints();
        headerGbc.gridx = 0;
        headerGbc.gridy = 0;
        headerGbc.weightx = 1.0;
        headerGbc.weighty = 0;
        headerGbc.fill = GridBagConstraints.HORIZONTAL;
        headerGbc.anchor = GridBagConstraints.NORTHWEST;
        varsPanel.add(headerPanel, headerGbc);
        
        String mappingsStr = properties.getOrDefault(propertyKey, "").toString();
        
        if (!mappingsStr.trim().isEmpty()) {
            String[] parts = mappingsStr.split(",");
            int index = 1;
            for (String part : parts) {
                String jsonKey = "";
                String varName = "";
                part = part.trim();
                if (part.contains("=")) {
                    String[] mapping = part.split("=", 2);
                    jsonKey = mapping[0].trim();
                    varName = mapping[1].trim();
                } else {
                    varName = part;
                }
                addMappingRowAt(varsPanel, properties, allVars, jsonKey, varName, index++, propertyKey);
            }
        } else {
            addMappingRowAt(varsPanel, properties, allVars, "", "", 1, propertyKey);
        }
        
        return varsPanel;
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
    
    private void updateMappings(JPanel varsPanel, Map<String, Object> properties, String propertyKey) {
        List<String> mappings = new ArrayList<>();
        
        for (Component comp : varsPanel.getComponents()) {
            if (comp instanceof JPanel) {
                JPanel rowPanel = (JPanel) comp;
                JTextField keyField = null;
                JComboBox<String> varCombo = null;
                
                for (Component rowComp : rowPanel.getComponents()) {
                    if (rowComp instanceof JTextField) {
                        keyField = (JTextField) rowComp;
                    } else if (rowComp instanceof JComboBox) {
                        @SuppressWarnings("unchecked")
                        JComboBox<String> cb = (JComboBox<String>) rowComp;
                        varCombo = cb;
                    }
                }
                
                if (varCombo != null) {
                    String varName = (String) varCombo.getSelectedItem();
                    if (varName != null && !varName.isEmpty()) {
                        String jsonKey = (keyField != null) ? keyField.getText().trim() : "";
                        if (!jsonKey.isEmpty()) {
                            mappings.add(jsonKey + "=" + varName);
                        } else {
                            mappings.add(varName);
                        }
                    }
                }
            }
        }
        
        String mappingsStr = String.join(", ", mappings);
        properties.put(propertyKey, mappingsStr);
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
        comboBox.setEditable(true);
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
    
    private void addMappingRowAt(JPanel varsPanel, Map<String, Object> properties, List<Slot> allVars, String jsonKey, String varName, int index, String propertyKey) {
        JPanel rowPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 2, 2, 2);
        
        // JSON Key field
        c.gridx = 0;
        c.weightx = 0.4;
        JTextField keyField = new JTextField(10);
        keyField.setText(jsonKey);
        keyField.addActionListener(e -> updateMappings(varsPanel, properties, propertyKey));
        keyField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                updateMappings(varsPanel, properties, propertyKey);
            }
        });
        rowPanel.add(keyField, c);
        
        // Variable combo
        c.gridx = 1;
        c.weightx = 0.6;
        JComboBox<String> comboBox = new JComboBox<>();
        comboBox.setEditable(true);
        comboBox.addItem(""); 
        for (Slot slot : allVars) {
            comboBox.addItem(slot.getName());
        }
        
        if (!varName.isEmpty()) {
            comboBox.setSelectedItem(varName);
        }
        
        comboBox.addActionListener(e -> updateMappings(varsPanel, properties, propertyKey));
        rowPanel.add(comboBox, c);
        
        // Plus button
        c.gridx = 2;
        c.weightx = 0;
        JButton plusButton = new JButton("+");
        plusButton.addActionListener(e -> {
            int idx = getRowIndex(varsPanel, rowPanel);
            addMappingRowAt(varsPanel, properties, allVars, "", "", idx + 1, propertyKey);
            varsPanel.revalidate();
            varsPanel.repaint();
            updateMappings(varsPanel, properties, propertyKey);
        });
        rowPanel.add(plusButton, c);
        
        // Minus button
        c.gridx = 3;
        JButton minusButton = new JButton("-");
        minusButton.addActionListener(e -> {
            varsPanel.remove(rowPanel);
            varsPanel.revalidate();
            varsPanel.repaint();
            updateMappings(varsPanel, properties, propertyKey);
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
        
        // Add header row
        JPanel headerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints hc = new GridBagConstraints();
        hc.fill = GridBagConstraints.HORIZONTAL;
        hc.insets = new Insets(2, 2, 2, 2);
        
        hc.gridx = 0;
        hc.weightx = 0.4;
        JLabel jsonKeyLabel = new JLabel("JsonKey");
        jsonKeyLabel.setFont(jsonKeyLabel.getFont().deriveFont(java.awt.Font.BOLD));
        headerPanel.add(jsonKeyLabel, hc);
        
        hc.gridx = 1;
        hc.weightx = 0;
        headerPanel.add(new JLabel(""), hc);
        
        hc.gridx = 2;
        hc.weightx = 0.4;
        JLabel variableLabel = new JLabel("Variable");
        variableLabel.setFont(variableLabel.getFont().deriveFont(java.awt.Font.BOLD));
        headerPanel.add(variableLabel, hc);
        
        hc.gridx = 3;
        hc.weightx = 0;
        headerPanel.add(new JLabel(""), hc);
        
        hc.gridx = 4;
        headerPanel.add(new JLabel(""), hc);
        
        GridBagConstraints headerGbc = new GridBagConstraints();
        headerGbc.gridx = 0;
        headerGbc.gridy = 0;
        headerGbc.weightx = 1.0;
        headerGbc.weighty = 0;
        headerGbc.fill = GridBagConstraints.HORIZONTAL;
        headerGbc.anchor = GridBagConstraints.NORTHWEST;
        mappingsPanel.add(headerPanel, headerGbc);
        
        String mappingsStr = properties.getOrDefault(RESPONSE_MAPPINGS, "").toString();
        
        if (!mappingsStr.trim().isEmpty()) {
            String[] mappings = mappingsStr.split(",");
            int index = 1;
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
            addResponseMappingRowAt(mappingsPanel, properties, allVars, "", "", 1);
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

    private JPanel createAuthorizationPanel(Map<String, Object> properties) {
        JPanel authPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Auth Type Dropdown
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        authPanel.add(new JLabel("Type:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.gridwidth = 3;
        String[] authTypes = {"None", "Bearer Token", "Basic Auth", "API Key"};
        JComboBox<String> authTypeCombo = new JComboBox<>(authTypes);
        authTypeCombo.setSelectedItem(properties.getOrDefault(AUTH_TYPE, "None"));
        authPanel.add(authTypeCombo, gbc);
        
        // Container for dynamic fields
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 4;
        gbc.weightx = 1.0;
        JPanel fieldsContainer = new JPanel(new CardLayout());
        authPanel.add(fieldsContainer, gbc);
        
        // None panel (empty)
        JPanel nonePanel = new JPanel();
        fieldsContainer.add(nonePanel, "None");
        
        // Bearer Token panel
        JPanel bearerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints bc = new GridBagConstraints();
        bc.insets = new Insets(2, 2, 2, 2);
        bc.fill = GridBagConstraints.HORIZONTAL;
        bc.gridx = 0;
        bc.gridy = 0;
        bc.weightx = 0;
        bearerPanel.add(new JLabel("Token:"), bc);
        bc.gridx = 1;
        bc.weightx = 1.0;
        JTextField bearerTokenField = new JTextField();
        bearerTokenField.setToolTipText("Enter token or ${variableName}");
        bearerPanel.add(bearerTokenField, bc);
        fieldsContainer.add(bearerPanel, "Bearer Token");
        
        // Basic Auth panel
        JPanel basicPanel = new JPanel(new GridBagLayout());
        GridBagConstraints bac = new GridBagConstraints();
        bac.insets = new Insets(2, 2, 2, 2);
        bac.fill = GridBagConstraints.HORIZONTAL;
        bac.gridx = 0;
        bac.gridy = 0;
        bac.weightx = 0;
        basicPanel.add(new JLabel("Username:"), bac);
        bac.gridx = 1;
        bac.weightx = 1.0;
        JTextField usernameField = new JTextField();
        usernameField.setToolTipText("Enter username or ${variableName}");
        basicPanel.add(usernameField, bac);
        bac.gridx = 0;
        bac.gridy = 1;
        bac.weightx = 0;
        basicPanel.add(new JLabel("Password:"), bac);
        bac.gridx = 1;
        bac.weightx = 1.0;
        JPasswordField passwordField = new JPasswordField();
        passwordField.setToolTipText("Enter password or ${variableName}");
        basicPanel.add(passwordField, bac);
        fieldsContainer.add(basicPanel, "Basic Auth");
        
        // API Key panel
        JPanel apiKeyPanel = new JPanel(new GridBagLayout());
        GridBagConstraints akc = new GridBagConstraints();
        akc.insets = new Insets(2, 2, 2, 2);
        akc.fill = GridBagConstraints.HORIZONTAL;
        akc.gridx = 0;
        akc.gridy = 0;
        akc.weightx = 0;
        apiKeyPanel.add(new JLabel("Header Name:"), akc);
        akc.gridx = 1;
        akc.weightx = 1.0;
        JTextField headerNameField = new JTextField();
        headerNameField.setToolTipText("e.g., x-api-key");
        apiKeyPanel.add(headerNameField, akc);
        akc.gridx = 0;
        akc.gridy = 1;
        akc.weightx = 0;
        apiKeyPanel.add(new JLabel("Value:"), akc);
        akc.gridx = 1;
        akc.weightx = 1.0;
        JTextField apiKeyValueField = new JTextField();
        apiKeyValueField.setToolTipText("Enter API key or ${variableName}");
        apiKeyPanel.add(apiKeyValueField, akc);
        fieldsContainer.add(apiKeyPanel, "API Key");
        
        // Parse existing AUTH_VALUE and populate fields
        String authValue = properties.getOrDefault(AUTH_VALUE, "").toString();
        String authType = properties.getOrDefault(AUTH_TYPE, "None").toString();
        
        if ("Bearer Token".equals(authType)) {
            bearerTokenField.setText(authValue);
        } else if ("Basic Auth".equals(authType)) {
            String[] parts = authValue.split(":", 2);
            if (parts.length >= 1) usernameField.setText(parts[0]);
            if (parts.length >= 2) passwordField.setText(parts[1]);
        } else if ("API Key".equals(authType)) {
            String[] parts = authValue.split(":", 2);
            if (parts.length >= 1) headerNameField.setText(parts[0]);
            if (parts.length >= 2) apiKeyValueField.setText(parts[1]);
        }
        
        // Update listeners
        bearerTokenField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { properties.put(AUTH_VALUE, bearerTokenField.getText()); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { properties.put(AUTH_VALUE, bearerTokenField.getText()); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { properties.put(AUTH_VALUE, bearerTokenField.getText()); }
        });
        
        javax.swing.event.DocumentListener basicAuthListener = new javax.swing.event.DocumentListener() {
            private void update() {
                properties.put(AUTH_VALUE, usernameField.getText() + ":" + new String(passwordField.getPassword()));
            }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
        };
        usernameField.getDocument().addDocumentListener(basicAuthListener);
        passwordField.getDocument().addDocumentListener(basicAuthListener);
        
        javax.swing.event.DocumentListener apiKeyListener = new javax.swing.event.DocumentListener() {
            private void update() {
                properties.put(AUTH_VALUE, headerNameField.getText() + ":" + apiKeyValueField.getText());
            }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
        };
        headerNameField.getDocument().addDocumentListener(apiKeyListener);
        apiKeyValueField.getDocument().addDocumentListener(apiKeyListener);
        
        // Switch panel based on selection
        authTypeCombo.addActionListener(e -> {
            String selectedType = (String) authTypeCombo.getSelectedItem();
            properties.put(AUTH_TYPE, selectedType);
            CardLayout cl = (CardLayout) fieldsContainer.getLayout();
            cl.show(fieldsContainer, selectedType);
        });
        
        // Show initial panel
        CardLayout cl = (CardLayout) fieldsContainer.getLayout();
        cl.show(fieldsContainer, authType);
        
        return authPanel;
    }
    
    private JPanel createHeadersPanel(Map<String, Object> properties) {
        JPanel headersPanel = new JPanel();
        headersPanel.setLayout(new BoxLayout(headersPanel, BoxLayout.Y_AXIS));
        
        String headersStr = properties.getOrDefault(CUSTOM_HEADERS, "").toString();
        Map<String, String> existingHeaders = new java.util.HashMap<>();
        if (!headersStr.isEmpty()) {
            for (String header : headersStr.split(",")) {
                String[] parts = header.split("=", 2);
                if (parts.length == 2) {
                    existingHeaders.put(parts[0].trim(), parts[1].trim());
                }
            }
        }
        
        for (Map.Entry<String, String> entry : existingHeaders.entrySet()) {
            addHeaderRow(headersPanel, properties, entry.getKey(), entry.getValue());
        }
        
        if (existingHeaders.isEmpty()) {
            addHeaderRow(headersPanel, properties, "", "");
        }
        
        return headersPanel;
    }
    
    private void addHeaderRow(JPanel headersPanel, Map<String, Object> properties, String key, String value) {
        JPanel rowPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 2, 2, 2);
        
        c.gridx = 0;
        c.weightx = 0.4;
        JTextField keyField = new JTextField(key);
        keyField.setToolTipText("Header name (e.g., x-api-key)");
        rowPanel.add(keyField, c);
        
        c.gridx = 1;
        c.weightx = 0.5;
        JTextField valueField = new JTextField(value);
        valueField.setToolTipText("Header value (supports ${variableName})");
        rowPanel.add(valueField, c);
        
        c.gridx = 2;
        c.weightx = 0;
        JButton plusButton = new JButton("+");
        plusButton.addActionListener(e -> {
            int idx = getRowIndex(headersPanel, rowPanel);
            addHeaderRowAt(headersPanel, properties, "", "", idx + 1);
            headersPanel.revalidate();
            headersPanel.repaint();
            updateCustomHeaders(headersPanel, properties);
        });
        rowPanel.add(plusButton, c);
        
        c.gridx = 3;
        JButton minusButton = new JButton("-");
        minusButton.addActionListener(e -> {
            headersPanel.remove(rowPanel);
            headersPanel.revalidate();
            headersPanel.repaint();
            updateCustomHeaders(headersPanel, properties);
        });
        rowPanel.add(minusButton, c);
        
        javax.swing.event.DocumentListener docListener = new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            private void update() {
                updateCustomHeaders(headersPanel, properties);
            }
        };
        keyField.getDocument().addDocumentListener(docListener);
        valueField.getDocument().addDocumentListener(docListener);
        
        headersPanel.add(rowPanel);
    }
    
    private void addHeaderRowAt(JPanel headersPanel, Map<String, Object> properties, String key, String value, int index) {
        JPanel rowPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 2, 2, 2);
        
        c.gridx = 0;
        c.weightx = 0.4;
        JTextField keyField = new JTextField(key);
        keyField.setToolTipText("Header name (e.g., x-api-key)");
        rowPanel.add(keyField, c);
        
        c.gridx = 1;
        c.weightx = 0.5;
        JTextField valueField = new JTextField(value);
        valueField.setToolTipText("Header value (supports ${variableName})");
        rowPanel.add(valueField, c);
        
        c.gridx = 2;
        c.weightx = 0;
        JButton plusButton = new JButton("+");
        plusButton.addActionListener(e -> {
            int idx = getRowIndex(headersPanel, rowPanel);
            addHeaderRowAt(headersPanel, properties, "", "", idx + 1);
            headersPanel.revalidate();
            headersPanel.repaint();
            updateCustomHeaders(headersPanel, properties);
        });
        rowPanel.add(plusButton, c);
        
        c.gridx = 3;
        JButton minusButton = new JButton("-");
        minusButton.addActionListener(e -> {
            headersPanel.remove(rowPanel);
            headersPanel.revalidate();
            headersPanel.repaint();
            updateCustomHeaders(headersPanel, properties);
        });
        rowPanel.add(minusButton, c);
        
        javax.swing.event.DocumentListener docListener = new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            private void update() {
                updateCustomHeaders(headersPanel, properties);
            }
        };
        keyField.getDocument().addDocumentListener(docListener);
        valueField.getDocument().addDocumentListener(docListener);
        
        headersPanel.add(rowPanel, index);
    }
    
    private void updateCustomHeaders(JPanel headersPanel, Map<String, Object> properties) {
        List<String> headers = new ArrayList<>();
        
        for (Component comp : headersPanel.getComponents()) {
            if (comp instanceof JPanel) {
                JPanel rowPanel = (JPanel) comp;
                JTextField keyField = null;
                JTextField valueField = null;
                
                for (Component rowComp : rowPanel.getComponents()) {
                    if (rowComp instanceof JTextField) {
                        if (keyField == null) {
                            keyField = (JTextField) rowComp;
                        } else {
                            valueField = (JTextField) rowComp;
                            break;
                        }
                    }
                }
                
                if (keyField != null && valueField != null) {
                    String key = keyField.getText().trim();
                    String value = valueField.getText().trim();
                    if (!key.isEmpty() && !value.isEmpty()) {
                        headers.add(key + "=" + value);
                    }
                }
            }
        }
        
        properties.put(CUSTOM_HEADERS, String.join(",", headers));
    }

    @Override
    protected void writeAttributes(XMLWriter out, IdMap uid_map) {
        Graph.printAtt(out, URL, this.getProperty(URL).toString());
        Graph.printAtt(out, HTTP_METHOD, this.getProperty(HTTP_METHOD).toString());
        Graph.printAtt(out, PATH_VARIABLES, this.getProperty(PATH_VARIABLES).toString());
        Graph.printAtt(out, QUERY_VARIABLES, this.getProperty(QUERY_VARIABLES).toString());
        Graph.printAtt(out, BODY_VARIABLES, this.getProperty(BODY_VARIABLES).toString());
        Graph.printAtt(out, RESPONSE_MODE, this.getProperty(RESPONSE_MODE).toString());
        Graph.printAtt(out, RESPONSE_MAPPINGS, this.getProperty(RESPONSE_MAPPINGS).toString());
        Graph.printAtt(out, RESPONSE_TARGET_VAR, this.getProperty(RESPONSE_TARGET_VAR).toString());
        Graph.printAtt(out, RESPONSE_AS_STRING, this.getProperty(RESPONSE_AS_STRING).toString());
        Graph.printAtt(out, AUTH_TYPE, this.getProperty(AUTH_TYPE).toString());
        Graph.printAtt(out, AUTH_VALUE, this.getProperty(AUTH_VALUE).toString());
        Graph.printAtt(out, CUSTOM_HEADERS, this.getProperty(CUSTOM_HEADERS).toString());
    }

    @Override
    protected void readAttribute(XMLReader r, String name, String value, IdMap uid_map) throws SAXException {
        if (name.equals(URL) || name.equals(HTTP_METHOD) || name.equals(PATH_VARIABLES) ||
            name.equals(QUERY_VARIABLES) || name.equals(BODY_VARIABLES) || name.equals(RESPONSE_MODE) ||
            name.equals(RESPONSE_MAPPINGS) || name.equals(RESPONSE_TARGET_VAR) || name.equals(RESPONSE_AS_STRING) ||
            name.equals(AUTH_TYPE) || name.equals(AUTH_VALUE) || name.equals(CUSTOM_HEADERS)) {
            this.setProperty(name, value);
        } else {
            super.readAttribute(r, name, value, uid_map);
        }
    }
    
    @Override
    public Color getPortColor(int portNumber) {
        if (portNumber == 0) {
            return new Color(0, 150, 0);
        } else if (portNumber == 1) {
            return new Color(200, 0, 0);
        }
        return super.getPortColor(portNumber);
    }
}
