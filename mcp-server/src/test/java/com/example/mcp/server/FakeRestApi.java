package com.example.mcp.server;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stand-in for the api-server: records every request and answers with a canned status and body.
 * Runs on a random local port so tests never depend on a real api-server.
 */
class FakeRestApi implements AutoCloseable {

    /** One request as the server received it. */
    record Request(String method, String path, String query, String contentType, String body) {
    }

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String responseBody = "{}";

    FakeRestApi() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new Request(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getRawQuery(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    body));
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            // A 204 must not carry a body; -1 tells HttpServer "no body".
            exchange.sendResponseHeaders(status, out.length == 0 ? -1 : out.length);
            if (out.length > 0) {
                exchange.getResponseBody().write(out);
            }
            exchange.close();
        });
        server.start();
    }

    /** Base URL to hand to {@link RestApiClient}, including the /api/v1 prefix. */
    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1";
    }

    /** Sets the response for subsequent requests. Use an empty body for 204. */
    void respondWith(int status, String body) {
        this.status = status;
        this.responseBody = body;
    }

    List<Request> requests() {
        return requests;
    }

    Request lastRequest() {
        return requests.getLast();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
