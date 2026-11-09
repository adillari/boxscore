package com.adillari.boxscore;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BoxScoreService {

    public static class IngestResult {
        public int stored;
        public int duplicates;
        public List<String> rejected = new ArrayList<>();
    }

    public static class TeamBox {
        public String teamId;
        public String name;
        public List<PlayerLine> players = new ArrayList<>();
        /** Stats credited to ids missing from the roster: in the totals, shown separately. */
        public List<PlayerLine> unattributed = new ArrayList<>();
        public PlayerLine totals = new PlayerLine();
    }

    public static class BoxScore {
        public String gameId;
        public List<TeamBox> teams = new ArrayList<>();
        public List<String> warnings = new ArrayList<>();
    }

    private final EventRepository events;
    private final BoxScoreCalculator calculator = new BoxScoreCalculator();
    private final Roster roster;

    public BoxScoreService(EventRepository events, ObjectMapper mapper) throws IOException {
        this.events = events;
        try (InputStream in = new ClassPathResource("roster.json").getInputStream()) {
            this.roster = mapper.readValue(in, Roster.class);
        }
    }

    public boolean knowsGame(String gameId) {
        return roster.gameId.equals(gameId);
    }

    @Transactional
    public IngestResult ingest(String gameId, List<Event> batch) {
        IngestResult out = new IngestResult();
        for (Event e : batch) {
            String problem = validate(gameId, e);
            if (problem != null) {
                out.rejected.add(problem);
            } else if (events.insertIfAbsent(e)) {
                out.stored++;
            } else {
                out.duplicates++;
            }
        }
        return out;
    }

    public BoxScore boxScore(String gameId) {
        BoxScoreCalculator.Result calc = calculator.calculate(events.findByGame(gameId));
        BoxScore box = new BoxScore();
        box.gameId = gameId;
        box.warnings.addAll(calc.warnings);

        Set<String> rostered = new HashSet<>();
        for (Roster.Team team : roster.teams) {
            TeamBox teamBox = new TeamBox();
            teamBox.teamId = team.teamId;
            teamBox.name = team.name;
            for (Roster.Player p : team.players) {
                rostered.add(p.playerId);
                PlayerLine line = calc.lines.getOrDefault(p.playerId, new PlayerLine());
                line.playerId = p.playerId;
                line.name = p.name;
                teamBox.players.add(line);
                teamBox.totals.add(line);
            }
            box.teams.add(teamBox);
        }

        // Anyone with stats who is not on the roster: keep the stat in the team total, but list it apart.
        calc.lines.forEach((playerId, line) -> {
            if (rostered.contains(playerId)) return;
            String teamId = calc.teamOf.get(playerId);
            box.warnings.add("Player " + playerId + " is not on the roster of " + teamId);
            for (TeamBox team : box.teams) {
                if (team.teamId.equals(teamId)) {
                    team.unattributed.add(line);
                    team.totals.add(line);
                }
            }
        });
        return box;
    }

    /** @return null when the player has neither a roster entry nor any stats. */
    public PlayerLine playerLine(String gameId, String playerId) {
        for (TeamBox team : boxScore(gameId).teams) {
            for (PlayerLine line : team.players) {
                if (playerId.equals(line.playerId)) {
                    return line;
                }
            }
            for (PlayerLine line : team.unattributed) {
                if (playerId.equals(line.playerId)) {
                    return line;
                }
            }
        }
        return null;
    }

    private String validate(String gameId, Event e) {
        if (e.eventId == null || e.seq == null || e.type == null) {
            return "Missing eventId, seq or type: " + e.eventId;
        }
        if (!gameId.equals(e.gameId)) {
            return "gameId does not match the URL: " + e.eventId;
        }
        return null;
    }
}
