package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpHandler;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import ru.practicum.moviehub.api.ErrorResponse;

import java.util.Collections;
import java.util.List;

abstract class BaseHttpHandler implements HttpHandler {
    protected static final String CT_JSON = "application/json; charset=UTF-8";

    protected final Gson gson = new Gson();

    protected void sendError(HttpExchange ex, int statusCode, String message)
            throws IOException {
        sendError(ex, statusCode, message, Collections.emptyList());
    }

    protected void sendError(HttpExchange ex, int statusCode, String message,
                             List<String> details) throws IOException {
        String jsonError = gson.toJson(new ErrorResponse(message, details));
        sendJson(ex, statusCode, jsonError);
    }

    protected void sendJson(HttpExchange ex, int status, String json)
            throws IOException {
        byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        ex.sendResponseHeaders(status, responseBytes.length);
        try (var outputStream = ex.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }

    protected void sendNoContent(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        ex.sendResponseHeaders(204, -1);
        ex.close();
    }
}