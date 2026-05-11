package ru.practicum.moviehub.http;

import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class MoviesHandler extends BaseHttpHandler {
    private final MoviesStore store;

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();

        try {
            switch (method) {
                case "GET":
                    handleGet(ex, path);
                    break;
                case "POST":
                    handlePost(ex);
                    break;
                case "DELETE":
                    handleDelete(ex, path);
                    break;
                default:
                    ex.sendResponseHeaders(405, -1);
                    ex.close();
                    break;
            }
        } catch (NumberFormatException e) {
            sendError(ex, 400, "ID в пути должен быть числом");
        } catch (Exception e) {
            sendError(ex, 500, "Внутренняя ошибка сервера");
        }
    }

    private void handleGet(HttpExchange ex, String path) throws IOException {
        if ("/movies".equals(path)) {
            String query = ex.getRequestURI().getQuery();

            if (query != null && query.startsWith("year=")) {
                try {
                    int year = Integer.parseInt(query.substring(5));
                    List<Movie> movies = store.getByYear(year);
                    String json = gson.toJson(movies);
                    sendJson(ex, 200, json);
                    return;
                } catch (NumberFormatException e) {
                    sendError(ex, 400, "Параметр 'year' должен быть целым числом");
                    return;
                }
            }

            List<Movie> movies = store.getAll();
            String json = gson.toJson(movies);
            sendJson(ex, 200, json);

        } else if (path.startsWith("/movies/")) {
            String idStr = path.substring("/movies/".length());

            try {
                long id = Long.parseLong(idStr);
                Optional<Movie> movie = store.getById(id);
                if (movie.isPresent()) {
                    sendJson(ex, 200, gson.toJson(movie.get()));
                } else {
                    sendError(ex, 404, "Фильм с ID " + id + " не найден");
                }
            } catch (NumberFormatException e) {
                sendError(ex, 400, "Некорректный ID");
            }
        } else {
            ex.sendResponseHeaders(404, -1);
            ex.close();
        }
    }

    private void handlePost(HttpExchange ex) throws IOException {
        // 1. Проверка Content-Type
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.startsWith("application/json")) {
            sendError(ex, 415, "Unsupported Media Type: ожидался application/json");
            return;
        }

        String jsonBody;
        try (Reader reader = new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8)) {
            jsonBody = new BufferedReader(reader).lines().collect(Collectors.joining("\n"));
        }

        try {
            JsonElement element = JsonParser.parseString(jsonBody);
            if (!element.isJsonObject()) {
                sendError(ex, 400, "Некорректный формат JSON: ожидался объект");
                return;
            }
            JsonObject obj = element.getAsJsonObject();

            if (!obj.has("title") || !obj.has("year")) {
                sendError(ex, 400,
                        "Некорректный формат JSON: отсутствуют обязательные поля (title, year)");
                return;
            }

            if (!obj.get("title").isJsonPrimitive() || !obj.get("year").isJsonPrimitive()) {
                sendError(ex, 400,
                        "Некорректный формат JSON: поля title и year должны быть примитивами");
                return;
            }
        } catch (JsonSyntaxException e) {
            sendError(ex, 400, "Некорректный формат JSON");
            return;
        }

        Movie movieFromRequest;
        try {
            movieFromRequest = gson.fromJson(jsonBody, Movie.class);
        } catch (JsonSyntaxException e) {
            sendError(ex, 400, "Некорректный формат JSON");
            return;
        }

        if (movieFromRequest.getTitle() == null || movieFromRequest.getTitle().trim().isEmpty()) {
            sendError(ex, 422, "Некорректные данные: название не должно быть пустым");
            return;
        }

        final int MAX_TITLE_LENGTH = 100;
        String title = movieFromRequest.getTitle();
        if (title.length() > MAX_TITLE_LENGTH) {
            sendError(ex, 422, "Некорректные данные",
                    List.of("название не должно превышать " + MAX_TITLE_LENGTH + " символов"));
            return;
        }

        int year = movieFromRequest.getYear();
        int currentYear = java.time.Year.now().getValue();

        if (year < 1888 || year > currentYear + 1) {
            sendError(ex, 422, "Некорректные данные",
                    List.of("год должен быть между 1888 и " + (currentYear + 1)));
            return;
        }

        Movie createdMovie = store.add(movieFromRequest.getTitle(), movieFromRequest.getYear());

        sendJson(ex, 201, gson.toJson(createdMovie));
    }

    private void handleDelete(HttpExchange ex, String path) throws IOException {
        if (path.matches("^/movies/\\d+$")) {
            long id = extractIdFromPath(path);

            if (store.remove(id)) {
                sendNoContent(ex); // 204 No Content
            } else {
                sendError(ex, 404, "Фильм с ID " + id + " не найден");
            }
        } else {
            ex.sendResponseHeaders(405, -1);
            ex.close();
        }
    }

    private long extractIdFromPath(String path) {
        String[] parts = path.split("/");
        return Long.parseLong(parts[parts.length - 1]);
    }
}

