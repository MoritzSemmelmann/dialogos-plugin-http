package com.clt.dialogos.httpplugin;

import com.clt.dialogos.plugin.PluginRuntime;

public class HttpPluginRuntime implements PluginRuntime {

    @Override
    public void dispose() {
        System.out.println("HttpPluginRuntime disposed");
    }
}