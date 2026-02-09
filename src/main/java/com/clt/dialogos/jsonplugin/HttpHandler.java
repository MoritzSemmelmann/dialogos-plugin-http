package com.clt.dialogos.jsonplugin;

import com.clt.diamant.Slot;
import com.clt.script.exp.Value;
import com.clt.script.exp.values.StringValue;
import org.json.JSONObject;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
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
            String customHeaders,
            boolean trustAllCertificates) {
        
        String url = buildUrlWithPathVariables(baseUrl, pathVarMappings, slotProvider);
        
        Map<String, String> resolvedQueryParams = buildQueryParameters(queryParams, slotProvider);
        
        String finalUrl = appendQueryParameters(url, resolvedQueryParams);
        
        try {
            HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10));

            if (trustAllCertificates) {
                clientBuilder = applyTrustAllCertificates(clientBuilder);
                System.out.println("WARNING: TLS certificate validation is disabled for this request.");
            }

            HttpClient client = clientBuilder.build();
            
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

            String previousHostnameProp = null;
            boolean hostnamePropChanged = false;
            if (trustAllCertificates) {
                previousHostnameProp = System.getProperty("jdk.internal.httpclient.disableHostnameVerification");
                System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
                hostnamePropChanged = true;
                System.out.println("Hostname verification has been disabled for java.net.http.HttpClient.");
            }

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
            } finally {
                if (hostnamePropChanged) {
                    if (previousHostnameProp != null) {
                        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", previousHostnameProp);
                    } else {
                        System.clearProperty("jdk.internal.httpclient.disableHostnameVerification");
                    }
                    System.out.println("Hostname verification property restored after trust-all request.");
                }
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

    private static HttpClient.Builder applyTrustAllCertificates(HttpClient.Builder builder) {
        try {
            TrustManager[] trustAllManagers = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllManagers, new SecureRandom());
            SSLParameters sslParameters = new SSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm(null);
            return builder.sslContext(sslContext).sslParameters(sslParameters);
        } catch (Exception e) {
            System.err.println("Failed to enable trust-all certificates: " + e.getMessage());
            return builder;
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
                String expression = parts[1].trim();
                
                if (expression.isEmpty()) {
                    continue;
                }
                
                Value value = JsonConverter.evaluateExpression(expression, slotProvider);
                String valueStr = valueToPlainString(value);
                url = url.replace("{" + pathVarName + "}", valueStr);
                System.out.println("Path variable: {" + pathVarName + "} = " + valueStr + " (from expression: " + expression + ")");
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
            String expression = entry.getValue();
            
            if (expression == null || expression.trim().isEmpty()) {
                continue;
            }
            Value value = JsonConverter.evaluateExpression(expression, slotProvider);
            String valueStr = valueToPlainString(value);
            params.put(paramKey, valueStr);
            System.out.println("Query parameter: " + paramKey + " = " + valueStr + " (from expression: " + expression + ")");
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
        
        switch (authType) {
            case "Bearer Token":
                if (authValue != null && !authValue.isEmpty()) {
                    Value tokenValue = JsonConverter.evaluateExpression(authValue, slotProvider);
                    String token = valueToPlainString(tokenValue);
                    requestBuilder.header("Authorization", "Bearer " + token);
                    System.out.println("Authorization: Bearer Token = " + token);
                }
                break;
            case "Basic Auth":
                if (authValue != null && !authValue.isEmpty()) {
                    String[] parts = authValue.split(":", 2);
                    if (parts.length == 2) {
                        Value usernameValue = JsonConverter.evaluateExpression(parts[0].trim(), slotProvider);
                        Value passwordValue = JsonConverter.evaluateExpression(parts[1].trim(), slotProvider);
                        String username = valueToPlainString(usernameValue);
                        String password = valueToPlainString(passwordValue);
                        String credentials = username + ":" + password;
                        String encoded = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
                        requestBuilder.header("Authorization", "Basic " + encoded);
                        System.out.println("Authorization: Basic Auth (username: " + username + ", password: " + password + ")");
                        System.out.println("Authorization header: Basic " + encoded);
                    }
                }
                break;
            case "API Key":
                if (authValue != null && !authValue.isEmpty()) {
                    String[] parts = authValue.split(":", 2);
                    if (parts.length == 2) {
                        String headerName = parts[0].trim();
                        Value headerValueObj = JsonConverter.evaluateExpression(parts[1].trim(), slotProvider);
                        String headerValue = valueToPlainString(headerValueObj);
                        requestBuilder.header(headerName, headerValue);
                        System.out.println("API Key: " + headerName + " = " + headerValue);
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
                String expression = parts[1].trim();
                
                Value valueObj = JsonConverter.evaluateExpression(expression, slotProvider);
                String value = valueToPlainString(valueObj);
                requestBuilder.header(key, value);
                System.out.println("Custom Header: " + key + " = " + value + " (from expression: " + expression + ")");
            }
        }
    }

    private static String valueToPlainString(Value value) {
        if (value instanceof StringValue) {
            return ((StringValue) value).getString();
        }
        return value.toString();
    }
}
