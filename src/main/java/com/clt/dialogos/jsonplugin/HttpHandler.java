package com.clt.dialogos.jsonplugin;

import com.clt.diamant.Slot;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class HttpHandler {
    
    public static class HttpResult {
        public final boolean success;
        public final String response;
        public final int statusCode;
        public final String errorMessage;
        
        public HttpResult(boolean success, String response, int statusCode, String errorMessage) {
            this.success = success;
            this.response = response;
            this.statusCode = statusCode;
            this.errorMessage = errorMessage;
        }
    }
    
    public static HttpResult sendHttpRequest(
            String baseUrl,
            String httpMethod,
            String[] pathVarMappings,
            Map<String, String> queryParams,
            JSONObject jsonBody,
            Function<String, Slot> slotProvider,
            String authType,
            String authValue,
            String customHeaders) {
        
        String url = buildUrlWithPathVariables(baseUrl, pathVarMappings, slotProvider);
        
        Map<String, String> resolvedQueryParams = buildQueryParameters(queryParams, slotProvider);
        
        String finalUrl = appendQueryParameters(url, resolvedQueryParams);
        
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
            
            addAuthorizationHeader(requestBuilder, authType, authValue, slotProvider);
            
            addCustomHeaders(requestBuilder, customHeaders, slotProvider);
            
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
            
            HttpResponse<String> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (HttpTimeoutException e) {
                System.err.println("\n✗ HTTP request timed out after 30 seconds");
                System.err.println("The server did not respond within the timeout period.");
                e.printStackTrace();
                return new HttpResult(false, null, 408, "Timeout: Server did not respond within 30 seconds");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("\n✗ HTTP request interrupted");
                return new HttpResult(false, null, 0, "Request interrupted");
            }
            
            System.out.println("Status Code: " + response.statusCode());
            System.out.println("Headers: " + response.headers().map());
            System.out.println("\nResponse Body:");
            System.out.println(response.body());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                System.out.println("✓ HTTP request successful");
                return new HttpResult(true, response.body(), response.statusCode(), null);
            } else {
                System.err.println("✗ HTTP request failed with status: " + response.statusCode());
                return new HttpResult(false, response.body(), response.statusCode(), "HTTP " + response.statusCode());
            }
            
        } catch (java.net.ConnectException e) {
            System.err.println("\n✗ Connection refused: The server is not reachable");
            System.err.println("Possible reasons: Wrong URL, server offline, firewall blocking");
            e.printStackTrace();
            return new HttpResult(false, null, 503, "Connection refused: Server not reachable");
        } catch (java.net.UnknownHostException e) {
            System.err.println("\n✗ Unknown host: The domain could not be resolved");
            System.err.println("Check if the URL is correct and you have internet connection");
            e.printStackTrace();
            return new HttpResult(false, null, 0, "Unknown host: Domain not found");
        } catch (java.io.IOException e) {
            System.err.println("\n✗ HTTP request IO error: " + e.getMessage());
            e.printStackTrace();
            return new HttpResult(false, null, 0, "IO Error: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("\n✗ HTTP request failed: " + e.getMessage());
            e.printStackTrace();
            return new HttpResult(false, null, 0, e.getMessage());
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
                String varOrValue = parts[1].trim();
                
                Slot slot = slotProvider.apply(varOrValue);
                if (slot != null) {
                    String value = slot.getValue().toString();
                    url = url.replace("{" + pathVarName + "}", value);
                    System.out.println("Path variable: {" + pathVarName + "} = " + value + " (from variable: " + varOrValue + ")");
                } else {
                    url = url.replace("{" + pathVarName + "}", varOrValue);
                    System.out.println("Path variable: {" + pathVarName + "} = " + varOrValue + " (literal value)");
                }
            }
        }
        
        return url;
    }
    
    private static Map<String, String> buildQueryParameters(
            Map<String, String> keyToVarMappings,
            Function<String, Slot> slotProvider) {
        
        Map<String, String> params = new HashMap<>();
        
        for (Map.Entry<String, String> entry : keyToVarMappings.entrySet()) {
            String paramKey = entry.getKey();
            String varOrValue = entry.getValue();
            
            Slot slot = slotProvider.apply(varOrValue);
            if (slot != null) {
                String value = slot.getValue().toString();
                params.put(paramKey, value);
                System.out.println("Query parameter: " + paramKey + " = " + value + " (from variable: " + varOrValue + ")");
            } else {
                params.put(paramKey, varOrValue);
                System.out.println("Query parameter: " + paramKey + " = " + varOrValue + " (literal value)");
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
    
    private static void addAuthorizationHeader(
            HttpRequest.Builder requestBuilder,
            String authType,
            String authValue,
            Function<String, Slot> slotProvider) {
        
        if (authType == null || authType.isEmpty() || authType.equals("None")) {
            return;
        }
        
        authValue = resolveVariables(authValue, slotProvider);
        
        switch (authType) {
            case "Bearer Token":
                if (authValue != null && !authValue.isEmpty()) {
                    requestBuilder.header("Authorization", "Bearer " + authValue);
                    System.out.println("Authorization: Bearer Token");
                }
                break;
            case "Basic Auth":
                if (authValue != null && !authValue.isEmpty()) {
                    String encoded = java.util.Base64.getEncoder().encodeToString(authValue.getBytes());
                    requestBuilder.header("Authorization", "Basic " + encoded);
                    System.out.println("Authorization: Basic Auth (credentials: " + authValue + ")");
                    System.out.println("Authorization header: Basic " + encoded);
                }
                break;
            case "API Key":
                if (authValue != null && !authValue.isEmpty()) {
                    String[] parts = authValue.split(":", 2);
                    if (parts.length == 2) {
                        requestBuilder.header(parts[0].trim(), parts[1].trim());
                        System.out.println("API Key: " + parts[0].trim());
                    }
                }
                break;
        }
    }
    
    private static void addCustomHeaders(
            HttpRequest.Builder requestBuilder,
            String customHeaders,
            Function<String, Slot> slotProvider) {
        
        if (customHeaders == null || customHeaders.trim().isEmpty()) {
            return;
        }
        
        String[] headers = customHeaders.split(",");
        for (String header : headers) {
            header = header.trim();
            if (header.isEmpty()) continue;
            
            String[] parts = header.split("=", 2);
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = resolveVariables(parts[1].trim(), slotProvider);
                requestBuilder.header(key, value);
                System.out.println("Custom Header: " + key + " = " + value);
            }
        }
    }
    
    private static String resolveVariables(String value, Function<String, Slot> slotProvider) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        
        String result = value;
        int start = 0;
        while ((start = result.indexOf("${", start)) != -1) {
            int end = result.indexOf("}", start);
            if (end == -1) break;
            
            String varName = result.substring(start + 2, end);
            Slot slot = slotProvider.apply(varName);
            if (slot != null) {
                String varValue = slot.getValue().toString();
                result = result.substring(0, start) + varValue + result.substring(end + 1);
                start += varValue.length();
            } else {
                start = end + 1;
            }
        }
        
        return result;
    }
}
