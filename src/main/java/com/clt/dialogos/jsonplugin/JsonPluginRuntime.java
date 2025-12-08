package com.clt.dialogos.jsonplugin;

import com.clt.dialogos.plugin.PluginRuntime;

/**
 * Runtime instance for the JSON Plugin
 */
public class JsonPluginRuntime implements PluginRuntime {

    @Override
    public void dispose() {
        // Cleanup runtime resources
        System.out.println("JsonPluginRuntime disposed");
    }
}
