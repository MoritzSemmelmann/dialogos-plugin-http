package com.clt.dialogos.httpplugin;

import com.clt.diamant.*;
import com.clt.diamant.graph.Graph;
import com.clt.diamant.graph.Node;
import com.clt.diamant.graph.nodes.NodeExecutionException;
import com.clt.script.exp.Value;
import com.clt.script.exp.values.StringValue;
import com.clt.xml.XMLReader;
import com.clt.xml.XMLWriter;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SendNode extends Node {

    private static final String VARIABLE_NAMES = "variableNames";
    private static final String HTTP_URL = "httpUrl";
    private static final String HTTP_METHOD = "httpMethod";
    private static final String PATH_VARIABLES = "pathVariables";
    private static final String QUERY_PARAMETERS = "queryParameters";
    private static final String AUTH_TYPE = "authType";
    private static final String AUTH_VALUE = "authValue";
    private static final String CUSTOM_HEADERS = "customHeaders";
    private static final String BODY_MODE = "bodyMode";
    private static final String RAW_BODY = "rawBody";
    private static final String TRUST_ALL_CERTS = "trustAllCerts";
    private static final String REMOVE_LABEL = "-";
    private static final Dimension COMPACT_BUTTON_SIZE = new Dimension(26, 22);
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

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
        this.setProperty(BODY_MODE, "mapping");
        this.setProperty(RAW_BODY, "");
        this.setProperty(TRUST_ALL_CERTS, "false");
    }
    
    @Override
    public void writeVoiceXML(XMLWriter w, IdMap uid_map) {}

    public static String getNodeTypeName(Class<?> c) {
        return "Http Send Node";
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
            String bodyMode = this.getProperty(BODY_MODE) == null ? "mapping" : this.getProperty(BODY_MODE).toString();
            
            String[] pathVarMappings = pathVarsStr.isEmpty() ? new String[0] : pathVarsStr.split(",");
            
            // Parse query parameter mappings: "key=var" or "var"
            Map<String, String> queryParamMappings = parseMappingsToMap(queryParamsStr);
            
            JSONObject jsonBody;
            if ("raw".equals(bodyMode)) {
                String rawJson = this.getProperty(RAW_BODY) == null ? "" : this.getProperty(RAW_BODY).toString();
                rawJson = substituteSlotValues(rawJson);
                jsonBody = parseRawJsonBody(rawJson);
            } else {
                // Parse body variable mappings: "jsonKey=var" or "var"
                Map<String, String> bodyVarMappings = parseMappingsToMap(varNamesStr);
                // Build JSON object from body variables
                jsonBody = JsonConverter.variablesToJson(bodyVarMappings, this::getSlotOrNull);
            }
            
            System.out.println("\n Generated JSON Body");
            System.out.println(jsonBody.toString(2));

            String authType = this.getProperty(AUTH_TYPE).toString();
            String authValue = this.getProperty(AUTH_VALUE).toString();
            String customHeaders = this.getProperty(CUSTOM_HEADERS).toString();
            boolean trustAllCerts = Boolean.parseBoolean(
                String.valueOf(this.getProperty(TRUST_ALL_CERTS))
            );
            
            // Send HTTP request with JSON object
            HttpHandler.HttpResult result = HttpHandler.sendHttpRequest(
                url,
                httpMethod,
                pathVarMappings,
                queryParamMappings,
                jsonBody,
                this::getSlotOrNull,
                authType,
                authValue,
                customHeaders,
                trustAllCerts
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
        
        final AtomicReference<List<String>> lastPathVars = new AtomicReference<>(new ArrayList<>());
        
        Runnable updatePathVars = () -> {
            properties.put(HTTP_URL, urlField.getText());
            List<String> pathVarNames = extractPathVariables(urlField.getText());
            
            if (!pathVarNames.equals(lastPathVars.get())) {
                lastPathVars.set(new ArrayList<>(pathVarNames));
                
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
        JPanel queryParamsPanel = createMappingPanel(properties, QUERY_PARAMETERS);
        JScrollPane queryScrollPane = new JScrollPane(queryParamsPanel);
        queryScrollPane.setPreferredSize(new Dimension(400, 120));
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
        headersScrollPane.setPreferredSize(new Dimension(400, 110));
        mainPanel.add(headersScrollPane, gbc);

        gbc.gridy = 8;
        gbc.weighty = 0;
        JCheckBox trustAllCheckbox = new JCheckBox("Trust all SSL certificates (insecure)");
        boolean trustAllSelected = Boolean.parseBoolean(
            properties.getOrDefault(TRUST_ALL_CERTS, "false").toString()
        );
        trustAllCheckbox.setSelected(trustAllSelected);
        trustAllCheckbox.addActionListener(
            e -> properties.put(TRUST_ALL_CERTS, Boolean.toString(trustAllCheckbox.isSelected()))
        );
        mainPanel.add(trustAllCheckbox, gbc);
        
        // JSON Body
        gbc.gridy = 9;
        gbc.weighty = 0;
        mainPanel.add(new JLabel("JSON Body:"), gbc);
        
        gbc.gridy = 10;
        gbc.weighty = 0.5;
        mainPanel.add(createBodyInputPanel(properties, VARIABLE_NAMES), gbc);
        
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

    private JSONObject parseRawJsonBody(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return new JSONObject();
        }
        try {
            return new JSONObject(rawJson);
        } catch (Exception e) {
            throw new NodeExecutionException(this, "Invalid raw JSON body: " + e.getMessage());
        }
    }

    private String substituteSlotValues(String template) {
        if (template == null || template.isEmpty()) {
            return template == null ? "" : template;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String varName = matcher.group(1).trim();
            String replacement = resolveSlotValue(varName);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String resolveSlotValue(String varName) {
        if (varName == null || varName.isEmpty()) {
            return "";
        }
        Slot slot = getSlotOrNull(varName);
        if (slot == null) {
            return "";
        }
        Value value = slot.getValue();
        if (value instanceof StringValue) {
            return ((StringValue) value).getString();
        }
        return value == null ? "" : value.toString();
    }

    
    private JPanel createMappingPanel(Map<String, Object> properties, String propertyKey) {
        List<Slot> allVars = this.getGraph().getAllVariables(Graph.LOCAL);
        JPanel rowsPanel = new JPanel();
        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));

        JButton addRowButton = new JButton("+");
        addRowButton.addActionListener(e -> {
            addMappingRow(rowsPanel, properties, allVars, "", "", propertyKey);
            rowsPanel.revalidate();
            rowsPanel.repaint();
        });

        String keyLabelText = propertyKey.equals(QUERY_PARAMETERS) ? "ParamKey" : "JsonKey";
        JPanel headerPanel = new JPanel(new BorderLayout());
        JPanel labelsPanel = new JPanel(new GridLayout(1, 2, 8, 0));
        JLabel jsonKeyLabel = new JLabel(keyLabelText);
        jsonKeyLabel.setFont(jsonKeyLabel.getFont().deriveFont(java.awt.Font.BOLD));
        JLabel variableLabel = new JLabel("Variable");
        variableLabel.setFont(variableLabel.getFont().deriveFont(java.awt.Font.BOLD));
        labelsPanel.add(jsonKeyLabel);
        labelsPanel.add(variableLabel);
        headerPanel.add(labelsPanel, BorderLayout.CENTER);
        JPanel addButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        addButtonPanel.add(addRowButton);
        headerPanel.add(addButtonPanel, BorderLayout.EAST);

        JPanel container = new JPanel(new BorderLayout());
        container.add(headerPanel, BorderLayout.NORTH);
        container.add(wrapRowsPanel(rowsPanel), BorderLayout.CENTER);

        String mappingsStr = properties.getOrDefault(propertyKey, "").toString();
        if (!mappingsStr.trim().isEmpty()) {
            String[] parts = mappingsStr.split(",");
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
                addMappingRow(rowsPanel, properties, allVars, jsonKey, varName, propertyKey);
            }
        }

        if (rowsPanel.getComponentCount() == 0) {
            addMappingRow(rowsPanel, properties, allVars, "", "", propertyKey);
        }
        
        return container;
    }

    private JPanel createBodyInputPanel(Map<String, Object> properties, String mappingPropertyKey) {
        JPanel container = new JPanel(new BorderLayout());

        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        ButtonGroup modeGroup = new ButtonGroup();
        JRadioButton mappingRadio = new JRadioButton("Use variable mappings");
        JRadioButton rawRadio = new JRadioButton("Enter raw JSON");
        modeGroup.add(mappingRadio);
        modeGroup.add(rawRadio);
        modePanel.add(mappingRadio);
        modePanel.add(rawRadio);
        container.add(modePanel, BorderLayout.NORTH);

        JPanel bodyCardPanel = new JPanel(new CardLayout());

        JPanel mappingPanel = createMappingPanel(properties, mappingPropertyKey);
        JScrollPane mappingScrollPane = new JScrollPane(mappingPanel);
        mappingScrollPane.setPreferredSize(new Dimension(400, 120));
        bodyCardPanel.add(mappingScrollPane, "mapping");

        JTextArea rawJsonArea = new JTextArea(8, 30);
        rawJsonArea.setLineWrap(true);
        rawJsonArea.setWrapStyleWord(true);
        rawJsonArea.setText(properties.getOrDefault(RAW_BODY, "").toString());
        rawJsonArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() {
                properties.put(RAW_BODY, rawJsonArea.getText());
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });
        JScrollPane rawScrollPane = new JScrollPane(rawJsonArea);
        rawScrollPane.setPreferredSize(new Dimension(400, 120));
        bodyCardPanel.add(rawScrollPane, "raw");

        container.add(bodyCardPanel, BorderLayout.CENTER);

        String mode = properties.getOrDefault(BODY_MODE, "mapping").toString();
        if ("raw".equals(mode)) {
            rawRadio.setSelected(true);
        } else {
            mappingRadio.setSelected(true);
            mode = "mapping";
        }

        CardLayout cardLayout = (CardLayout) bodyCardPanel.getLayout();
        cardLayout.show(bodyCardPanel, mode);

        mappingRadio.addActionListener(e -> {
            properties.put(BODY_MODE, "mapping");
            cardLayout.show(bodyCardPanel, "mapping");
        });
        rawRadio.addActionListener(e -> {
            properties.put(BODY_MODE, "raw");
            cardLayout.show(bodyCardPanel, "raw");
        });

        return container;
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
    
    private void addMappingRow(JPanel rowsPanel, Map<String, Object> properties, List<Slot> allVars, String jsonKey, String varName, String propertyKey) {
        JPanel rowPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 2, 2, 2);
        rowPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        // JSON Key field
        c.gridx = 0;
        c.weightx = 0.4;
        JTextField keyField = new JTextField(10);
        keyField.setText(jsonKey);
        keyField.addActionListener(e -> updateMappings(rowsPanel, properties, propertyKey));
        keyField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                updateMappings(rowsPanel, properties, propertyKey);
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
        
        comboBox.addActionListener(e -> updateMappings(rowsPanel, properties, propertyKey));
        rowPanel.add(comboBox, c);
        
        // Minus button
        c.gridx = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        JButton minusButton = new JButton(REMOVE_LABEL);
        styleCompactButton(minusButton);
        minusButton.addActionListener(e -> {
            rowsPanel.remove(rowPanel);
            rowsPanel.revalidate();
            rowsPanel.repaint();
            updateMappings(rowsPanel, properties, propertyKey);
            if (rowsPanel.getComponentCount() == 0) {
                addMappingRow(rowsPanel, properties, allVars, "", "", propertyKey);
            }
        });
        rowPanel.add(minusButton, c);
        
        rowsPanel.add(rowPanel);
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
        JComboBox<String> bearerTokenField = new JComboBox<>();
        bearerTokenField.setEditable(true);
        bearerTokenField.addItem("");
        List<Slot> allVars = this.getGraph().getAllVariables(Graph.LOCAL);
        for (Slot slot : allVars) {
            bearerTokenField.addItem(slot.getName());
        }
        bearerTokenField.setToolTipText("Select variable or enter token");
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
        JComboBox<String> usernameField = new JComboBox<>();
        usernameField.setEditable(true);
        usernameField.addItem("");
        for (Slot slot : allVars) {
            usernameField.addItem(slot.getName());
        }
        usernameField.setToolTipText("Select variable or enter username");
        basicPanel.add(usernameField, bac);
        bac.gridx = 0;
        bac.gridy = 1;
        bac.weightx = 0;
        basicPanel.add(new JLabel("Password:"), bac);
        bac.gridx = 1;
        bac.weightx = 1.0;
        JComboBox<String> passwordField = new JComboBox<>();
        passwordField.setEditable(true);
        passwordField.addItem("");
        for (Slot slot : allVars) {
            passwordField.addItem(slot.getName());
        }
        passwordField.setToolTipText("Select variable or enter password");
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
        JComboBox<String> apiKeyValueField = new JComboBox<>();
        apiKeyValueField.setEditable(true);
        apiKeyValueField.addItem("");
        for (Slot slot : allVars) {
            apiKeyValueField.addItem(slot.getName());
        }
        apiKeyValueField.setToolTipText("Select variable or enter API key");
        apiKeyPanel.add(apiKeyValueField, akc);
        fieldsContainer.add(apiKeyPanel, "API Key");
        
        // Parse existing AUTH_VALUE and populate fields
        String authValue = properties.getOrDefault(AUTH_VALUE, "").toString();
        String authType = properties.getOrDefault(AUTH_TYPE, "None").toString();
        
        if ("Bearer Token".equals(authType)) {
            bearerTokenField.setSelectedItem(authValue);
        } else if ("Basic Auth".equals(authType)) {
            String[] parts = authValue.split(":", 2);
            if (parts.length >= 1) usernameField.setSelectedItem(parts[0]);
            if (parts.length >= 2) passwordField.setSelectedItem(parts[1]);
        } else if ("API Key".equals(authType)) {
            String[] parts = authValue.split(":", 2);
            if (parts.length >= 1) headerNameField.setText(parts[0]);
            if (parts.length >= 2) apiKeyValueField.setSelectedItem(parts[1]);
        }
        
        // Update listeners
        bearerTokenField.addActionListener(e -> {
            if ("Bearer Token".equals(authTypeCombo.getSelectedItem())) {
                properties.put(AUTH_VALUE, bearerTokenField.getSelectedItem());
            }
        });
        
        ActionListener basicAuthListener = e -> {
            if ("Basic Auth".equals(authTypeCombo.getSelectedItem())) {
                properties.put(AUTH_VALUE, usernameField.getSelectedItem() + ":" + passwordField.getSelectedItem());
            }
        };
        usernameField.addActionListener(basicAuthListener);
        passwordField.addActionListener(basicAuthListener);
        
        ActionListener apiKeyListener = e -> {
            if ("API Key".equals(authTypeCombo.getSelectedItem())) {
                properties.put(AUTH_VALUE, headerNameField.getText() + ":" + apiKeyValueField.getSelectedItem());
            }
        };
        headerNameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { 
                if ("API Key".equals(authTypeCombo.getSelectedItem())) {
                    properties.put(AUTH_VALUE, headerNameField.getText() + ":" + apiKeyValueField.getSelectedItem());
                }
            }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { 
                if ("API Key".equals(authTypeCombo.getSelectedItem())) {
                    properties.put(AUTH_VALUE, headerNameField.getText() + ":" + apiKeyValueField.getSelectedItem());
                }
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { 
                if ("API Key".equals(authTypeCombo.getSelectedItem())) {
                    properties.put(AUTH_VALUE, headerNameField.getText() + ":" + apiKeyValueField.getSelectedItem());
                }
            }
        });
        apiKeyValueField.addActionListener(apiKeyListener);
        
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
        List<Slot> allVars = this.getGraph().getAllVariables(Graph.LOCAL);
        JPanel rowsPanel = new JPanel();
        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));

        JButton addHeaderButton = new JButton("+");
        addHeaderButton.addActionListener(e -> {
            addHeaderRow(rowsPanel, properties, "", "", allVars);
            rowsPanel.revalidate();
            rowsPanel.repaint();
        });

        JPanel headerPanel = new JPanel(new BorderLayout());
        JPanel labelsPanel = new JPanel(new GridLayout(1, 2, 8, 0));
        JLabel headerLabel = new JLabel("Header");
        headerLabel.setFont(headerLabel.getFont().deriveFont(java.awt.Font.BOLD));
        JLabel valueLabel = new JLabel("Value");
        valueLabel.setFont(valueLabel.getFont().deriveFont(java.awt.Font.BOLD));
        labelsPanel.add(headerLabel);
        labelsPanel.add(valueLabel);
        headerPanel.add(labelsPanel, BorderLayout.CENTER);
        JPanel addButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        addButtonPanel.add(addHeaderButton);
        headerPanel.add(addButtonPanel, BorderLayout.EAST);

        JPanel container = new JPanel(new BorderLayout());
        container.add(headerPanel, BorderLayout.NORTH);
        container.add(wrapRowsPanel(rowsPanel), BorderLayout.CENTER);

        String headersStr = properties.getOrDefault(CUSTOM_HEADERS, "").toString();
        if (!headersStr.isEmpty()) {
            for (String header : headersStr.split(",")) {
                String[] parts = header.split("=", 2);
                if (parts.length == 2) {
                    addHeaderRow(rowsPanel, properties, parts[0].trim(), parts[1].trim(), allVars);
                }
            }
        }

        if (rowsPanel.getComponentCount() == 0) {
            addHeaderRow(rowsPanel, properties, "", "", allVars);
        }

        return container;
    }

    private void addHeaderRow(JPanel rowsPanel, Map<String, Object> properties, String key, String value, List<Slot> allVars) {
        JPanel rowPanel = new JPanel(new GridBagLayout());
        rowPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
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
        JComboBox<String> valueField = new JComboBox<>();
        valueField.setEditable(true);
        valueField.addItem("");
        for (Slot slot : allVars) {
            valueField.addItem(slot.getName());
        }
        valueField.setSelectedItem(value);
        valueField.setToolTipText("Select variable or enter value");
        rowPanel.add(valueField, c);

        c.gridx = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        JButton minusButton = new JButton(REMOVE_LABEL);
        styleCompactButton(minusButton);
        minusButton.addActionListener(e -> {
            rowsPanel.remove(rowPanel);
            rowsPanel.revalidate();
            rowsPanel.repaint();
            updateCustomHeaders(rowsPanel, properties);
            if (rowsPanel.getComponentCount() == 0) {
                addHeaderRow(rowsPanel, properties, "", "", allVars);
            }
        });
        rowPanel.add(minusButton, c);

        javax.swing.event.DocumentListener docListener = new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            private void update() {
                updateCustomHeaders(rowsPanel, properties);
            }
        };
        keyField.getDocument().addDocumentListener(docListener);
        valueField.addActionListener(e -> updateCustomHeaders(rowsPanel, properties));

        rowsPanel.add(rowPanel);
    }

    private JPanel wrapRowsPanel(JPanel rowsPanel) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(rowsPanel, BorderLayout.NORTH);
        wrapper.add(Box.createVerticalGlue(), BorderLayout.CENTER);
        return wrapper;
    }

    private void styleCompactButton(JButton button) {
        button.setText(REMOVE_LABEL);
        button.setMargin(new Insets(0, 4, 0, 4));
        button.setPreferredSize(COMPACT_BUTTON_SIZE);
        button.setMinimumSize(COMPACT_BUTTON_SIZE);
        button.setMaximumSize(COMPACT_BUTTON_SIZE);
    }

    private void updateCustomHeaders(JPanel headersPanel, Map<String, Object> properties) {
        List<String> headers = new ArrayList<>();
        
        for (Component comp : headersPanel.getComponents()) {
            if (comp instanceof JPanel) {
                JPanel rowPanel = (JPanel) comp;
                JTextField keyField = null;
                Object valueField = null;
                
                for (Component rowComp : rowPanel.getComponents()) {
                    if (rowComp instanceof JTextField) {
                        keyField = (JTextField) rowComp;
                    } else if (rowComp instanceof JComboBox) {
                        valueField = rowComp;
                    }
                }
                
                if (keyField != null && valueField != null) {
                    String key = keyField.getText().trim();
                    String value = "";
                    if (valueField instanceof JComboBox) {
                        @SuppressWarnings("unchecked")
                        JComboBox<String> combo = (JComboBox<String>) valueField;
                        value = (String) combo.getSelectedItem();
                        if (value == null) value = "";
                    }
                    value = value.trim();
                    
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
        Graph.printAtt(out, BODY_MODE, this.getProperty(BODY_MODE).toString());
        Graph.printAtt(out, RAW_BODY, this.getProperty(RAW_BODY).toString());
        Graph.printAtt(out, TRUST_ALL_CERTS, this.getProperty(TRUST_ALL_CERTS).toString());
    }

    @Override
    protected void readAttribute(XMLReader r, String name, String value, IdMap uid_map) throws SAXException {
        if (name.equals(VARIABLE_NAMES) || name.equals(HTTP_URL) || name.equals(HTTP_METHOD) ||
            name.equals(PATH_VARIABLES) || name.equals(QUERY_PARAMETERS) ||
            name.equals(AUTH_TYPE) || name.equals(AUTH_VALUE) || name.equals(CUSTOM_HEADERS) ||
            name.equals(BODY_MODE) || name.equals(RAW_BODY) || name.equals(TRUST_ALL_CERTS)) {
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
