package com.adillari.boxscore;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/games/{id}")
public class GameController {
    private final BoxScoreService service;

    public GameController(BoxScoreService service) {
        this.service = service;
    }

    /** A single object also binds here: accept-single-value-as-array is enabled. */
    @PostMapping("/events")
    public BoxScoreService.IngestResult ingest(@PathVariable String id, @RequestBody List<Event> events) {
        requireGame(id);
        return service.ingest(id, events);
    }

    @GetMapping("/boxscore")
    public BoxScoreService.BoxScore boxScore(@PathVariable String id) {
        requireGame(id);
        return service.boxScore(id);
    }

    @GetMapping("/players/{playerId}")
    public PlayerLine player(@PathVariable String id, @PathVariable String playerId) {
        requireGame(id);
        PlayerLine line = service.playerLine(id, playerId);
        if (line == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown player");
        }
        return line;
    }

    private void requireGame(String id) {
        if (!service.knowsGame(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown game");
        }
    }
}
