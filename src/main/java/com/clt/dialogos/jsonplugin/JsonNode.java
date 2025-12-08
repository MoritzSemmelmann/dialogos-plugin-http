package com.clt.dialogos.jsonplugin;

import com.clt.diamant.ExecutionLogger;
import com.clt.diamant.IdMap;
import com.clt.diamant.InputCenter;
import com.clt.diamant.WozInterface;
import com.clt.diamant.graph.Node;
import com.clt.xml.XMLWriter;
import org.xml.sax.SAXException;

import javax.swing.*;
import java.util.Map;

/**
 * A JSON node for DialogOS
 */
public class JsonNode extends Node {

    public static String getNodeTypeName(Class<?> c) {
        return "JSON Node";
    }

    @Override
    public Node execute(WozInterface comm, InputCenter input, ExecutionLogger logger) {
        logNode(logger);
        System.out.println("JsonNode executed!");
        
        // Return the next node via the first edge
        return getEdge(0).getTarget();
    }

    @Override
    public JComponent createEditorComponent(Map<String, Object> properties) {
        // Return a simple panel or null for no custom editor
        return null;
    }

    @Override
    protected void writeAttributes(XMLWriter out, IdMap uid_map) {
        // No custom attributes to save
    }

    protected void readAttribute(org.xml.sax.helpers.DefaultHandler handler, String name, String value, IdMap uid_map) throws SAXException {
        // No custom attributes to read
    }
}
