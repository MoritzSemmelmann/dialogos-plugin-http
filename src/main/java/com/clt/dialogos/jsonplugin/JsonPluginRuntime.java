package com.clt.dialogos.jsonplugin;

import com.clt.dialogos.plugin.PluginRuntime;

public class JsonPluginRuntime implements PluginRuntime {

    @Override
    public void dispose() {
        System.out.println("JsonPluginRuntime disposed");
    }
}
