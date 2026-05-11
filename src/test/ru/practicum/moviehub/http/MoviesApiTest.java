package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.*;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MoviesApiTest {
    private static final String BASE = "http://localhost:8080";
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private static final Gson gson = new Gson();
    private static final Type MOVIE_LIST_TYPE = new TypeToken<List<Movie>>() {
    }.getType();

    private MoviesStore store;
    private MoviesServer server;

    private void sendPost(String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        client.send(req, HttpResponse.BodyHandlers.discarding());
    }

    @BeforeEach
    void setUp() {
        store = new MoviesStore();
        server = new MoviesServer(store);
        server.start();
        store.clear();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @Order(1)
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        assertEquals(200, resp.statusCode());
        assertEquals("application/json; charset=UTF-8", resp.headers().firstValue("Content-Type")
                .orElse(""));
        assertEquals("[]", resp.body().trim());
    }

    @Test
    @Order(2)
    void postMovie_withValidData_returnsCreated() throws Exception {
        Movie movieToSend = new Movie(0, "Inception", 2010);
        String jsonBody = gson.toJson(movieToSend);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(201, resp.statusCode());
        Movie responseMovie = gson.fromJson(resp.body(), Movie.class);
        assertEquals("Inception", responseMovie.getTitle());
        assertEquals(2010, responseMovie.getYear());
        assertNotNull(responseMovie.getId());
        assertTrue(responseMovie.getId() > 0);
    }

    @Test
    @Order(3)
    void getMovies_afterAdd_returnsList() throws Exception {
        Movie movieToSend = new Movie(0, "The Matrix", 1999);
        String jsonBody = gson.toJson(movieToSend);

        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        client.send(postReq, HttpResponse.BodyHandlers.discarding());

        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(getReq, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());

        List<Movie> movies = gson.fromJson(resp.body(), MOVIE_LIST_TYPE);

        assertFalse(movies.isEmpty());

        Movie added = movies.get(0);
        assertEquals("The Matrix", added.getTitle());
        assertEquals(1999, added.getYear());
    }

    @Test
    @Order(4)
    void postMovie_withEmptyTitle_returns422() throws Exception {
        Movie movieToSend = new Movie(0, "", 2010);
        String jsonBody = gson.toJson(movieToSend);

        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE + "/movies"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(422, resp.statusCode());

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);
        assertTrue(error.getError().contains("Некорректные данные") ||
                error.getDetails().toString().contains("название не должно быть пустым"));
    }

    @Test
    @Order(5)
    void postMovie_withTooLongTitle_returns422() throws Exception {
        String longTitle = "A".repeat(101);
        Movie movieToSend = new Movie(0, longTitle, 2010);

        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE + "/movies"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(movieToSend)))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(422, resp.statusCode());

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);
        assertTrue(error.getDetails().contains("название не должно превышать 100 символов"));
    }

    @Test
    @Order(6)
    void postMovie_withInvalidYear_returns422() throws Exception {
        Movie movieToSend = new Movie(0, "Film", 1800);

        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE + "/movies"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(movieToSend)))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(422, resp.statusCode());

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);
        assertTrue(error.getDetails().get(0).contains("год должен быть между"));

        int futureYear = java.time.Year.now().getValue() + 2;
        movieToSend = new Movie(0, "Future Film", futureYear);

        resp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE + "/movies"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(movieToSend)))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(422, resp.statusCode());
        error = gson.fromJson(resp.body(), ErrorResponse.class);
        assertTrue(error.getDetails().get(0).contains("год должен быть между"));
    }


    @Test
    @Order(7)
    void postMovie_withWrongContentType_returns415() throws Exception {
        Movie movieToSend = new Movie(0, "Film", 2010);

        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE + "/movies"))
                        .header("Content-Type", "text/plain")
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(movieToSend)))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(415, resp.statusCode());
    }

    @Test
    @Order(8)
    void getMovieById_existingId_returnsMovie() throws Exception {
        Movie movieToSend = new Movie(0, "Interstellar", 2014);
        String jsonBody = gson.toJson(movieToSend);

        HttpResponse<String> postResp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE + "/movies"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        Movie createdMovie = gson.fromJson(postResp.body(), Movie.class);
        long id = createdMovie.getId();

        HttpResponse<String> getResp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE + "/movies/" + id))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, getResp.statusCode());

        Movie fetchedMovie = gson.fromJson(getResp.body(), Movie.class);
        assertEquals(id, fetchedMovie.getId());
        assertEquals("Interstellar", fetchedMovie.getTitle());
    }

    @Test
    @Order(9)
    void getMovieById_nonExistingId_returns404() throws Exception {
        long nonExistingId = 999999;

        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE + "/movies/" + nonExistingId))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(404, resp.statusCode());

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);

        assertEquals("Фильм с ID " + nonExistingId + " не найден", error.getError());
    }

    @Test
    @Order(10)
    void getMovieById_invalidIdFormat_returns400() throws Exception {
        String invalidId = "abc";

        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE + "/movies/" + invalidId))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(400, resp.statusCode());

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);
        assertTrue(error.getError().contains("Некорректный ID"));
    }

    @Test
    @Order(11)
    void deleteMovie_existingId_returns204() throws Exception {
        Movie movieToSend = new Movie(0, "For Deletion", 2025);
        String jsonBody = gson.toJson(movieToSend);

        HttpResponse<String> postResp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE + "/movies"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        Movie createdMovie = gson.fromJson(postResp.body(), Movie.class);
        long id = createdMovie.getId();

        HttpResponse<Void> deleteResp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE + "/movies/" + id))
                        .DELETE()
                        .build(),
                HttpResponse.BodyHandlers.discarding()
        );

        assertEquals(204, deleteResp.statusCode());

        HttpResponse<String> listResp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE + "/movies"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        List<Movie> moviesAfterDelete = gson.fromJson(listResp.body(), new ListOfMoviesTypeToken().getType());
        moviesAfterDelete.forEach(m -> assertNotEquals(id, m.getId()));
    }

    @Test
    @Order(12)
    void deleteMovie_nonExistingId_returns404() throws Exception {
        long nonExistingId = 999999;

        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE + "/movies/" + nonExistingId))
                        .DELETE()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(404, resp.statusCode());

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);
        assertEquals("Фильм с ID " + nonExistingId + " не найден", error.getError());
    }

    @Test
    @Order(13)
    void getMovies_filterByYear_returnsFilteredList() throws Exception {
        // Добавляем несколько фильмов разных лет
        sendPost(gson.toJson(new Movie(0, "Film A", 2015)));
        sendPost(gson.toJson(new Movie(0, "Film B", 2015)));
        sendPost(gson.toJson(new Movie(0, "Film C", 2016)));

        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE + "/movies?year=2015"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, resp.statusCode());

        List<Movie> movies = gson.fromJson(resp.body(), new ListOfMoviesTypeToken().getType());
        assertEquals(2, movies.size());
        movies.forEach(m -> assertEquals(2015, m.getYear()));
    }

    @Test
    @Order(14)
    void getMovies_filterByYear_noResults_returnsEmptyArray() throws Exception {
        int yearWithNoMovies = 3000;

        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE + "/movies?year=" + yearWithNoMovies))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, resp.statusCode());
        List<Movie> movies = gson.fromJson(resp.body(), new ListOfMoviesTypeToken().getType());
        assertTrue(movies.isEmpty());
    }

    @Test
    @Order(15)
    void getMovies_filterByYear_invalidParam_returns400() throws Exception {
        String invalidYearParam = "not_a_number";

        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE + "/movies?year=" + invalidYearParam))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(400, resp.statusCode());

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);
        assertTrue(error.getError().contains("Параметр 'year' должен быть целым числом"));
    }

    @Test
    @Order(16)
    void postMovie_withInvalidJson_returns400() throws Exception {
        String invalidJson = "{title: \"Inception\", year: \"две тысячи десять\"}";

        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE + "/movies"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(invalidJson))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(400, resp.statusCode());
    }
}
