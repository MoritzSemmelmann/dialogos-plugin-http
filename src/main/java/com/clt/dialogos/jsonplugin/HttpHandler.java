package com.clt.dialogos.jsonplugin;

public class HttpHandler {
    public static void sendHttpRequest(String url, String pathVariables, String queryParameters, String jsonPayload) {
        System.out.println("Sending HTTP request to URL: " + url);
        System.out.println("Path Variables: " + pathVariables);
        System.out.println("Query Parameters: " + queryParameters);
        System.out.println("JSON Payload: " + jsonPayload);
    }
}
