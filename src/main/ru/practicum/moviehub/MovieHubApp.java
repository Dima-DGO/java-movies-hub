package ru.practicum.moviehub;

import ru.practicum.moviehub.http.MoviesServer;
import ru.practicum.moviehub.store.MoviesStore;

public class MovieHubApp {
    public static void main(String[] args) {
        final MoviesStore store = new MoviesStore();

        final MoviesServer server = new MoviesServer(store);

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        server.start();
    }
}
