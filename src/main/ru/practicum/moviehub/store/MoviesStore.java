package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class MoviesStore {
    private final Map<Long, Movie> movies = new HashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    public List<Movie> getAll() {
        return new ArrayList<>(movies.values());
    }

    public List<Movie> getByYear(int year) {
        return movies.values().stream()
                .filter(m -> m.getYear() == year)
                .collect(Collectors.toList());
    }

    public Optional<Movie> getById(long id) {
        return Optional.ofNullable(movies.get(id));
    }

    public Movie add(String title, int year) {
        long newId = idSequence.getAndIncrement();
        Movie movie = new Movie(newId, title, year);
        movies.put(newId, movie);
        return movie;
    }

    public boolean remove(long id) {
        return movies.remove(id) != null;
    }

    public void clear() {
        movies.clear();
        idSequence.set(1); // Сброс последовательности ID
    }

}
