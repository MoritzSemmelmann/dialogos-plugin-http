package com.clt.dialogos.jsonplugin;

import com.clt.diamant.Slot;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class HttpHandler {
    
    public static String sendHttpRequest(
            String baseUrl,
            String httpMethod,
            String[] pathVarMappings,
            String[] queryParamVars,
            JSONObject jsonBody,
            Function<String, Slot> slotProvider) {
        
        String url = buildUrlWithPathVariables(baseUrl, pathVarMappings, slotProvider);
        
        Map<String, String> queryParams = buildQueryParameters(queryParamVars, slotProvider);
        
        String finalUrl = appendQueryParameters(url, queryParams);
        
        System.out.println("Method: " + httpMethod);
        System.out.println("URL: " + finalUrl);
        System.out.println("\nJSON Body:");
        System.out.println(jsonBody.toString(2));
        
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
            
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(finalUrl))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30));
            
            switch (httpMethod.toUpperCase()) {
                case "GET":
                    requestBuilder.GET();
                    break;
                case "POST":
                    requestBuilder.POST(HttpRequest.BodyPublishers.ofString(jsonBody.toString()));
                    break;
                case "PUT":
                    requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(jsonBody.toString()));
                    break;
                case "DELETE":
                    requestBuilder.DELETE();
                    break;
                case "PATCH":
                    requestBuilder.method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody.toString()));
                    break;
                default:
                    requestBuilder.POST(HttpRequest.BodyPublishers.ofString(jsonBody.toString()));
            }
            
            HttpRequest request = requestBuilder.build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.println("Status Code: " + response.statusCode());
            System.out.println("Headers: " + response.headers().map());
            System.out.println("\nResponse Body:");
            System.out.println(response.body());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                System.out.println("✓ HTTP request successful");
                return response.body();
            } else {
                System.err.println("✗ HTTP request failed with status: " + response.statusCode());
                throw new RuntimeException("HTTP request failed with status: " + response.statusCode());
            }
            
        } catch (Exception e) {
            System.err.println("\n✗ HTTP request failed: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("HTTP request failed: " + e.getMessage(), e);
        }
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
