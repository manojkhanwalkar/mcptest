package com.example.mcp.server;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Thin HTTP client for the api-server REST API (see src/main/resources/openapi/api.yaml in the
 * api-server project). It knows nothing about MCP; {@link RestApiTools} maps tool calls onto it.
 */
public class RestApiClient {

    /** Status code and raw body of a REST call. The body is JSON, problem+json, or empty. */
    public record ApiResponse(int status, String body) {
        public boolean isSuccess() {
            return status >= 200 && status < 300;
        }
    }

    private final String baseUrl;
    private final HttpClient http;

    /**
     * @param baseUrl API root including the version prefix, e.g. {@code http://localhost:8080/api/v1}
     */
    public RestApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public String baseUrl() {
        return baseUrl;
    }

    /**
     * Sends one request.
     *
     * @param method   HTTP method (GET, POST, PUT, DELETE)
     * @param path     path below the base URL, e.g. {@code /items/3}
     * @param query    query parameters; null values are skipped
     * @param jsonBody request body, or null for none
     */
    public ApiResponse send(String method, String path, Map<String, Object> query, String jsonBody)
            throws IOException, InterruptedException {
        HttpRequest.BodyPublisher body = jsonBody == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(jsonBody);
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path + queryString(query)))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json, application/problem+json")
                .method(method, body);
        if (jsonBody != null) {
            request.header("Content-Type", "application/json");
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new ApiResponse(response.statusCode(), response.body());
    }

    private static String queryString(Map<String, Object> query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        String joined = query.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(e -> encode(e.getKey()) + "=" + encode(String.valueOf(e.getValue())))
                .collect(Collectors.joining("&"));
        return joined.isEmpty() ? "" : "?" + joined;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
