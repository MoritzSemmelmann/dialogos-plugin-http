package com.clt.dialogos.jsonplugin;

import com.clt.dialogos.plugin.PluginSettings;
import com.clt.diamant.graph.Node;
import com.clt.gui.Images;

import javax.swing.*;
import java.util.Arrays;

/**
 * JSON Plugin for DialogOS
 */
public class JsonPlugin implements com.clt.dialogos.plugin.Plugin {

    @Override
    public String getId() {
        return "com.clt.dialogos.jsonplugin";
    }

    @Override
    public String getName() {
        return "JSON Plugin";
    }

    @Override
    public Icon getIcon() {
        // Return a simple icon or null
        return UIManager.getIcon("FileView.fileIcon");
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public void initialize() {
        // Register custom node types
        Node.registerNodeTypes(
            getId(),
            Arrays.<Class<?>>asList(JsonNode.class)
        );
    }

    @Override
    public void terminate() {
        // Cleanup resources if needed
    }

    @Override
    public PluginSettings createDefaultSettings() {
        return new JsonPluginSettings();
    }
}
