package com.clt.dialogos.jsonplugin;

import com.clt.dialogos.plugin.PluginRuntime;
import com.clt.dialogos.plugin.PluginSettings;
import com.clt.diamant.IdMap;
import com.clt.xml.XMLReader;
import com.clt.xml.XMLWriter;
import org.xml.sax.SAXException;

import javax.swing.*;
import java.awt.*;

/**
 * Settings for the JSON Plugin
 */
public class JsonPluginSettings extends PluginSettings {

    @Override
    public void writeAttributes(XMLWriter out, IdMap uidMap) {
        // Write any persistent settings here
    }

    @Override
    protected void readAttribute(XMLReader r, String name, String value, IdMap uid_map) throws SAXException {
        // Read any persistent settings here
    }

    @Override
    public JComponent createEditor() {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel("JSON Plugin Settings");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    @Override
    protected PluginRuntime createRuntime(Component parent) throws Exception {
        return new JsonPluginRuntime();
    }
}
