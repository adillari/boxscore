package com.adillari.boxscore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EventRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public EventRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** @return true if stored, false if this eventId already existed. */
    public boolean insertIfAbsent(Event e) {
        return jdbc.update(
            "INSERT INTO events (event_id, game_id, seq, type, payload) VALUES (?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE event_id = event_id",
            e.eventId, e.gameId, e.seq, e.type, toJson(e)) == 1;
    }

    public List<Event> findByGame(String gameId) {
        return jdbc.query("SELECT payload FROM events WHERE game_id = ? ORDER BY seq",
            (rs, n) -> fromJson(rs.getString("payload")), gameId);
    }

    private String toJson(Event e) {
        try {
            return mapper.writeValueAsString(e);
        } catch (JsonProcessingException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private Event fromJson(String json) {
        try {
            return mapper.readValue(json, Event.class);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
