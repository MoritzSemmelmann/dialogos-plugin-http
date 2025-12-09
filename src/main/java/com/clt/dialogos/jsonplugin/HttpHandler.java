package com.clt.dialogos.jsonplugin;

import com.clt.diamant.Slot;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class HttpHandler {
    
    public static void sendHttpRequest(
            String baseUrl,
            String[] pathVarMappings,
            String[] queryParamVars,
            JSONObject jsonBody,
            Function<String, Slot> slotProvider) {
        
        String url = buildUrlWithPathVariables(baseUrl, pathVarMappings, slotProvider);
        
        Map<String, String> queryParams = buildQueryParameters(queryParamVars, slotProvider);
        
        String finalUrl = appendQueryParameters(url, queryParams);
        
        System.out.println("URL: " + finalUrl);
        System.out.println("\nJSON Body:");
        System.out.println(jsonBody.toString(2));
        
        // TODO: Actual HTTP request implementation
    }
    
    private static String buildUrlWithPathVariables(
            String baseUrl,
            String[] pathVarMappings,
            Function<String, Slot> slotProvider) {
        
        String url = baseUrl;
        
        for (String mapping : pathVarMappings) {
            mapping = mapping.trim();
            if (mapping.isEmpty()) continue;
            
            String[] parts = mapping.split("=");
            if (parts.length == 2) {
                String pathVarName = parts[0].trim();
                String variableName = parts[1].trim();
                
                Slot slot = slotProvider.apply(variableName);
                if (slot != null) {
                    String value = slot.getValue().toString();
                    url = url.replace("{" + pathVarName + "}", value);
                    System.out.println("Path variable: {" + pathVarName + "} = " + value);
                }
            }
        }
        
        return url;
    }
    
    private static Map<String, String> buildQueryParameters(
            String[] queryParamVars,
            Function<String, Slot> slotProvider) {
        
        Map<String, String> params = new HashMap<>();
        
        for (String varName : queryParamVars) {
            varName = varName.trim();
            if (varName.isEmpty()) continue;
            
            Slot slot = slotProvider.apply(varName);
            if (slot != null) {
                String value = slot.getValue().toString();
                params.put(varName, value);
                System.out.println("Query parameter: " + varName + " = " + value);
            }
        }
        
        return params;
    }
    
    private static String appendQueryParameters(String url, Map<String, String> params) {
        if (params.isEmpty()) {
            return url;
        }
        
        StringBuilder sb = new StringBuilder(url);
        sb.append("?");
        
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                sb.append("&");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        
        return sb.toString();
    }
}
