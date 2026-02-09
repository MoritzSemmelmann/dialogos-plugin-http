package com.clt.dialogos.jsonplugin;

import com.clt.diamant.*;
import com.clt.diamant.graph.Graph;
import com.clt.diamant.graph.Node;
import com.clt.diamant.graph.nodes.NodeExecutionException;
import com.clt.script.exp.Value;
import com.clt.xml.XMLReader;
import com.clt.xml.XMLWriter;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final String BODY_MODE = "bodyMode";
    private static final String RAW_BODY = "rawBody";
    private static final String REMOVE_LABEL = "-";
    private static final Dimension COMPACT_BUTTON_SIZE = new Dimension(26, 22);
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

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
        this.setProperty(BODY_MODE, "mapping");
        this.setProperty(RAW_BODY, "");
    }

    @Override
    public void writeVoiceXML(XMLWriter w, IdMap uid_map) {
        // no VoiceXML support
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
            String bodyMode = this.getProperty(BODY_MODE) == null ? "mapping" : this.getProperty(BODY_MODE).toString();
            JSONObject jsonBody;
            if ("raw".equals(bodyMode)) {
                String rawJson = this.getProperty(RAW_BODY) == null ? "" : this.getProperty(RAW_BODY).toString();
                rawJson = substituteSlotValues(rawJson);
                jsonBody = parseRawJsonBody(rawJson);
            } else {
                Map<String, String> bodyVarMappings = parseMappingsToMap(bodyVarsStr);
                jsonBody = JsonConverter.variablesToJson(bodyVarMappings, this::getSlotOrNull);
            }

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
            
            Object responsePayload = parseResponsePayload(result.response);
            JSONObject responseJson = responsePayload instanceof JSONObject
                ? (JSONObject) responsePayload
                : wrapArrayResponse((JSONArray) responsePayload);

            String responseMode = this.getProperty(RESPONSE_MODE).toString();
            if ("single".equals(responseMode)) {
                // Single variable mode
                String targetVar = this.getProperty(RESPONSE_TARGET_VAR).toString().trim();
                boolean asString = Boolean.parseBoolean(this.getProperty(RESPONSE_AS_STRING).toString());
                JsonConverter.mapJsonToSingleVariable(responsePayload, result.response, targetVar, asString, this::getSlot);
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
        Object value = slot.getValue();
        return value == null ? "" : value.toString();
    }

    private Object parseResponsePayload(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return new JSONObject();
        }
        String trimmed = responseBody.trim();
        if (trimmed.startsWith("[")) {
            return new JSONArray(trimmed);
        }
        return new JSONObject(trimmed);
    }

    private JSONObject wrapArrayResponse(JSONArray array) {
        JSONObject wrapper = new JSONObject();
        wrapper.put("$root", array);
        return wrapper;
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

    private JPanel createBodyInputPanel(Map<String, Object> properties) {
        JPanel container = new JPanel(new BorderLayout());

        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        ButtonGroup group = new ButtonGroup();
        JRadioButton mappingsRadio = new JRadioButton("Use variable mappings");
        JRadioButton rawRadio = new JRadioButton("Enter raw JSON");
        group.add(mappingsRadio);
        group.add(rawRadio);
        modePanel.add(mappingsRadio);
        modePanel.add(rawRadio);
        container.add(modePanel, BorderLayout.NORTH);

        JPanel cardPanel = new JPanel(new CardLayout());

        JPanel mappingPanel = createMappingPanel(properties, BODY_VARIABLES);
        JScrollPane mappingScrollPane = new JScrollPane(mappingPanel);
        mappingScrollPane.setPreferredSize(new Dimension(400, 100));
        cardPanel.add(mappingScrollPane, "mapping");

        JTextArea rawArea = new JTextArea(8, 30);
        rawArea.setLineWrap(true);
        rawArea.setWrapStyleWord(true);
        rawArea.setText(properties.getOrDefault(RAW_BODY, "").toString());
        rawArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() {
                properties.put(RAW_BODY, rawArea.getText());
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });
        JScrollPane rawScrollPane = new JScrollPane(rawArea);
        rawScrollPane.setPreferredSize(new Dimension(400, 120));
        cardPanel.add(rawScrollPane, "raw");

        container.add(cardPanel, BorderLayout.CENTER);

        String currentMode = properties.getOrDefault(BODY_MODE, "mapping").toString();
        if ("raw".equals(currentMode)) {
            rawRadio.setSelected(true);
        } else {
            mappingsRadio.setSelected(true);
            currentMode = "mapping";
        }

        CardLayout layout = (CardLayout) cardPanel.getLayout();
        layout.show(cardPanel, currentMode);

        mappingsRadio.addActionListener(e -> {
            properties.put(BODY_MODE, "mapping");
            layout.show(cardPanel, "mapping");
        });
        rawRadio.addActionListener(e -> {
            properties.put(BODY_MODE, "raw");
            layout.show(cardPanel, "raw");
        });

        return container;
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
        gbc.weighty = 0.2;
        JPanel headersPanel = createHeadersPanel(properties);
        JScrollPane headersScrollPane = new JScrollPane(headersPanel);
        headersScrollPane.setPreferredSize(new Dimension(400, 110));
        mainPanel.add(headersScrollPane, gbc);
        
        // JSON Body
        gbc.gridy = 8;
        gbc.weighty = 0;
        mainPanel.add(new JLabel("JSON Body:"), gbc);
        
        gbc.gridy = 9;
        gbc.weighty = 0.3;
        mainPanel.add(createBodyInputPanel(properties), gbc);
        
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
        List<Slot> allVars = this.getGraph().getAllVariables(Graph.LOCAL);
        JPanel rowsPanel = new JPanel();
        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));

        JButton addRowButton = new JButton("+");
        addRowButton.addActionListener(e -> {
            addMappingRow(rowsPanel, properties, allVars, "", "", propertyKey);
            rowsPanel.revalidate();
            rowsPanel.repaint();
        });

        String keyLabelText = propertyKey.equals(QUERY_VARIABLES) ? "ParamKey" : "JsonKey";
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
        rowPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 2, 2, 2);

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

    private JPanel createResponseMappingPanel(Map<String, Object> properties) {
        List<Slot> allVars = this.getGraph().getAllVariables(Graph.LOCAL);
        JPanel rowsPanel = new JPanel();
        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));

        JButton addRowButton = new JButton("+");
        addRowButton.addActionListener(e -> {
            addResponseMappingRow(rowsPanel, properties, allVars, "", "");
            rowsPanel.revalidate();
            rowsPanel.repaint();
        });

        JPanel headerPanel = new JPanel(new BorderLayout());
        JPanel labelsPanel = new JPanel(new GridLayout(1, 2, 8, 0));
        JLabel jsonKeyLabel = new JLabel("JsonKey");
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

        String mappingsStr = properties.getOrDefault(RESPONSE_MAPPINGS, "").toString();
        if (!mappingsStr.trim().isEmpty()) {
            String[] mappings = mappingsStr.split(",");
            for (String mapping : mappings) {
                mapping = mapping.trim();
                if (!mapping.isEmpty()) {
                    String[] parts = mapping.split("=");
                    String jsonKey = parts.length > 0 ? parts[0].trim() : "";
                    String varName = parts.length > 1 ? parts[1].trim() : "";
                    addResponseMappingRow(rowsPanel, properties, allVars, jsonKey, varName);
                }
            }
        }

        if (rowsPanel.getComponentCount() == 0) {
            addResponseMappingRow(rowsPanel, properties, allVars, "", "");
        }

        return container;
    }

    private void addResponseMappingRow(JPanel rowsPanel, Map<String, Object> properties,
                                       List<Slot> allVars, String jsonKey, String varName) {
        JPanel rowPanel = new JPanel(new GridBagLayout());
        rowPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 2, 2, 2);

        c.gridx = 0;
        c.weightx = 0.4;
        JTextField jsonKeyField = new JTextField(jsonKey, 10);
        jsonKeyField.addActionListener(e -> updateResponseMappings(rowsPanel, properties));
        jsonKeyField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                updateResponseMappings(rowsPanel, properties);
            }
        });
        jsonKeyField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            private void update() {
                updateResponseMappings(rowsPanel, properties);
            }
        });
        rowPanel.add(jsonKeyField, c);

        c.gridx = 1;
        c.weightx = 0;
        rowPanel.add(new JLabel(" = "), c);

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

        comboBox.addActionListener(e -> updateResponseMappings(rowsPanel, properties));
        rowPanel.add(comboBox, c);

        c.gridx = 3;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        JButton minusButton = new JButton(REMOVE_LABEL);
        styleCompactButton(minusButton);
        minusButton.addActionListener(e -> {
            rowsPanel.remove(rowPanel);
            rowsPanel.revalidate();
            rowsPanel.repaint();
            updateResponseMappings(rowsPanel, properties);
            if (rowsPanel.getComponentCount() == 0) {
                addResponseMappingRow(rowsPanel, properties, allVars, "", "");
            }
        });
        rowPanel.add(minusButton, c);

        rowsPanel.add(rowPanel);
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
        Graph.printAtt(out, BODY_MODE, this.getProperty(BODY_MODE).toString());
        Graph.printAtt(out, RAW_BODY, this.getProperty(RAW_BODY).toString());
    }

    @Override
    protected void readAttribute(XMLReader r, String name, String value, IdMap uid_map) throws SAXException {
        if (name.equals(URL) || name.equals(HTTP_METHOD) || name.equals(PATH_VARIABLES) ||
            name.equals(QUERY_VARIABLES) || name.equals(BODY_VARIABLES) || name.equals(RESPONSE_MODE) ||
            name.equals(RESPONSE_MAPPINGS) || name.equals(RESPONSE_TARGET_VAR) || name.equals(RESPONSE_AS_STRING) ||
            name.equals(AUTH_TYPE) || name.equals(AUTH_VALUE) || name.equals(CUSTOM_HEADERS) ||
            name.equals(BODY_MODE) || name.equals(RAW_BODY)) {
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

