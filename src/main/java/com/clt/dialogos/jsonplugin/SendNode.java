package com.clt.dialogos.jsonplugin;

import com.clt.diamant.*;
import com.clt.diamant.graph.Graph;
import com.clt.diamant.graph.Node;
import com.clt.diamant.graph.nodes.NodeExecutionException;
import com.clt.xml.XMLReader;
import com.clt.xml.XMLWriter;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SendNode extends Node {

    private static final String VARIABLE_NAMES = "variableNames";
    private static final String HTTP_URL = "httpUrl";
    private static final String HTTP_METHOD = "httpMethod";
    private static final String PATH_VARIABLES = "pathVariables";
    private static final String QUERY_PARAMETERS = "queryParameters";
    private static final String AUTH_TYPE = "authType";
    private static final String AUTH_VALUE = "authValue";
    private static final String CUSTOM_HEADERS = "customHeaders";

    public SendNode() {
        this.addEdge("Success");
        this.addEdge("Error");
        
        this.getEdge(0).setColor(new Color(0, 150, 0));
        this.getEdge(1).setColor(new Color(200, 0, 0)); 
        
        this.setProperty(VARIABLE_NAMES, "");
        this.setProperty(HTTP_URL, "");
        this.setProperty(HTTP_METHOD, "POST");
        this.setProperty(PATH_VARIABLES, "");
        this.setProperty(QUERY_PARAMETERS, "");
        this.setProperty(AUTH_TYPE, "None");
        this.setProperty(AUTH_VALUE, "");
        this.setProperty(CUSTOM_HEADERS, "");
    }

    public static String getNodeTypeName(Class<?> c) {
        return "Send JSON";
    }

    @Override
    public Node execute(WozInterface comm, InputCenter input, ExecutionLogger logger) {
        logNode(logger);
        
        try {
            String url = this.getProperty(HTTP_URL).toString();
            String httpMethod = this.getProperty(HTTP_METHOD).toString();
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

            String authType = this.getProperty(AUTH_TYPE).toString();
            String authValue = this.getProperty(AUTH_VALUE).toString();
            String customHeaders = this.getProperty(CUSTOM_HEADERS).toString();
            
            // Send HTTP request with JSON object
            HttpHandler.HttpResult result = HttpHandler.sendHttpRequest(
                url,
                httpMethod,
                pathVarMappings,
                queryParamVars,
                jsonBody,
                this::getSlot,
                authType,
                authValue,
                customHeaders
            );
            
            if (result.success) {
                return getEdge(0).getTarget(); // Success edge
            } else {
                return getEdge(1).getTarget(); // Error edge
            }
            
        } catch (Exception e) {
            System.err.println("Error in SendNode: " + e.getMessage());
            return getEdge(1).getTarget(); // Error edge
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
        methodComboBox.setSelectedItem(properties.getOrDefault(HTTP_METHOD, "POST"));
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
        urlField.setText(properties.getOrDefault(HTTP_URL, "").toString());
        
        JPanel urlContainer = new JPanel(new BorderLayout());
        urlContainer.add(urlField, BorderLayout.NORTH);

        JPanel pathVarsPanel = new JPanel(new GridBagLayout());
        JScrollPane pathScrollPane = new JScrollPane(pathVarsPanel);
        pathScrollPane.setPreferredSize(new Dimension(400, 100));
        pathScrollPane.setBorder(BorderFactory.createTitledBorder("Path Variables"));
        urlContainer.add(pathScrollPane, BorderLayout.CENTER);
        
        final List<String>[] lastPathVars = new List[]{new ArrayList<>()};
        
        Runnable updatePathVars = () -> {
            properties.put(HTTP_URL, urlField.getText());
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
        gbc.weighty = 0.5;
        JPanel queryParamsPanel = createVariablePanel(properties, QUERY_PARAMETERS);
        JScrollPane queryScrollPane = new JScrollPane(queryParamsPanel);
        queryScrollPane.setPreferredSize(new Dimension(400, 100));
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
        gbc.weighty = 0.3;
        JPanel headersPanel = createHeadersPanel(properties);
        JScrollPane headersScrollPane = new JScrollPane(headersPanel);
        headersScrollPane.setPreferredSize(new Dimension(400, 80));
        mainPanel.add(headersScrollPane, gbc);
        
        // JSON Body Variables
        gbc.gridy = 8;
        gbc.weighty = 0;
        mainPanel.add(new JLabel("JSON Body Variables:"), gbc);
        
        gbc.gridy = 9;
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
        
        // Parse existing headers
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
        Graph.printAtt(out, VARIABLE_NAMES, this.getProperty(VARIABLE_NAMES).toString());
        Graph.printAtt(out, HTTP_URL, this.getProperty(HTTP_URL).toString());
        Graph.printAtt(out, HTTP_METHOD, this.getProperty(HTTP_METHOD).toString());
        Graph.printAtt(out, PATH_VARIABLES, this.getProperty(PATH_VARIABLES).toString());
        Graph.printAtt(out, QUERY_PARAMETERS, this.getProperty(QUERY_PARAMETERS).toString());
        Graph.printAtt(out, AUTH_TYPE, this.getProperty(AUTH_TYPE).toString());
        Graph.printAtt(out, AUTH_VALUE, this.getProperty(AUTH_VALUE).toString());
        Graph.printAtt(out, CUSTOM_HEADERS, this.getProperty(CUSTOM_HEADERS).toString());
    }

    @Override
    protected void readAttribute(XMLReader r, String name, String value, IdMap uid_map) throws SAXException {
        if (name.equals(VARIABLE_NAMES) || name.equals(HTTP_URL) || name.equals(HTTP_METHOD) ||
            name.equals(PATH_VARIABLES) || name.equals(QUERY_PARAMETERS) ||
            name.equals(AUTH_TYPE) || name.equals(AUTH_VALUE) || name.equals(CUSTOM_HEADERS)) {
            this.setProperty(name, value);
        } else {
            super.readAttribute(r, name, value, uid_map);
        }
    }
    
    @Override
    public Color getPortColor(int portNumber) {
        if (portNumber == 0) {
            return new Color(0, 150, 0); // Success - green
        } else if (portNumber == 1) {
            return new Color(200, 0, 0); // Error - red
        }
        return super.getPortColor(portNumber);
    }
}
