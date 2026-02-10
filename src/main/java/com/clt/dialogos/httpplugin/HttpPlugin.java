package com.clt.dialogos.httpplugin;

import com.clt.dialogos.plugin.PluginSettings;
import com.clt.diamant.graph.Node;

import javax.swing.*;
import java.util.Arrays;

public class HttpPlugin implements com.clt.dialogos.plugin.Plugin {

    @Override
    public String getId() {
        return "com.clt.dialogos.httpplugin";
    }

    @Override
    public String getName() {
        return "HTTP Plugin";
    }

    @Override
    public Icon getIcon() {
        return UIManager.getIcon("FileView.fileIcon");
    }

    @Override
    public String getVersion() {
        return "1.0.22";
    }

    @Override
    public void initialize() {
        // Register custom node types
        Node.registerNodeTypes(
            getId(),
            Arrays.<Class<?>>asList(SendNode.class, SendAndReceiveNode.class)
        );
    }

    @Override
    public void terminate() {}

    @Override
    public PluginSettings createDefaultSettings() {
        return new HttpPluginSettings();
    }
}
