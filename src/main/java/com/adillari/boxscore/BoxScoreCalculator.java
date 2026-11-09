package com.adillari.boxscore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure function: events in, stat lines out. No Spring or database, so every
 * edge case is unit-testable. Arrival order does not matter because it replays by seq.
 */
public class BoxScoreCalculator {

    public static class Result {
        /** Keyed by playerId, including ids that are not on the roster. */
        public final Map<String, PlayerLine> lines = new LinkedHashMap<>();
        /** Keyed by playerId: the team credited for that player's events. */
        public final Map<String, String> teamOf = new HashMap<>();
        public final List<String> warnings = new ArrayList<>();
    }

    public Result calculate(List<Event> input) {
        Result result = new Result();

        Map<String, Event> byId = new LinkedHashMap<>();
        input.stream()
             .sorted(Comparator.comparing((Event e) -> e.seq))
             .forEach(e -> byId.putIfAbsent(e.eventId, e));

        Set<String> voided = new HashSet<>();
        Map<String, Map<String, Object>> updates = new HashMap<>();
        for (Event e : byId.values()) {
            if (!"CORRECTION".equals(e.type)) continue;
            if (!byId.containsKey(e.refEventId) || e.correction == null) {
                result.warnings.add("Correction " + e.eventId + " ignored: unknown target " + e.refEventId);
            } else if ("VOID".equals(e.correction.action)) {
                voided.add(e.refEventId);
            } else if ("UPDATE".equals(e.correction.action) && e.correction.fields != null) {
                updates.computeIfAbsent(e.refEventId, k -> new HashMap<>()).putAll(e.correction.fields);
            }
        }

        for (Event original : byId.values()) {
            if (voided.contains(original.eventId) || "CORRECTION".equals(original.type)) continue;
            Event e = updates.containsKey(original.eventId)
                    ? original.withFields(updates.get(original.eventId)) : original;

            if ("ASSIST".equals(e.type)) {
                Event shot = byId.get(e.refEventId);
                if (shot == null) {
                    result.warnings.add("Assist " + e.eventId + " not counted: unknown shot " + e.refEventId);
                    continue;
                }
                if (voided.contains(e.refEventId)) continue; // no basket, no assist
            }
            apply(e, result);
        }
        return result;
    }

    private void apply(Event e, Result result) {
        if (e.playerId == null) return; // PERIOD_START, PERIOD_END, GAME_END
        PlayerLine line = result.lines.computeIfAbsent(e.playerId, id -> {
            PlayerLine created = new PlayerLine();
            created.playerId = id;
            return created;
        });
        result.teamOf.putIfAbsent(e.playerId, e.teamId);

        boolean three = "3PT".equals(e.shotType);
        switch (e.type) {
            case "SHOT_MADE":
                line.fgm++;
                line.fga++;
                line.pts += three ? 3 : 2;
                if (three) {
                    line.threePm++;
                    line.threePa++;
                }
                break;
            case "SHOT_MISSED":
                line.fga++;
                if (three) {
                    line.threePa++;
                }
                break;
            case "FREE_THROW_MADE":
                line.ftm++;
                line.fta++;
                line.pts++;
                break;
            case "FREE_THROW_MISSED":
                line.fta++;
                break;
            case "REBOUND":
                line.reb++;
                break;
            case "ASSIST":
                line.ast++;
                break;
            case "TURNOVER":
                line.to++;
                break;
            case "FOUL":
                line.pf++;
                break;
            case "SUB_IN":
            case "SUB_OUT":
                break; // substitutions carry no stat
            default:
                result.warnings.add("Unknown event type ignored: " + e.type + " (" + e.eventId + ")");
        }
    }
}
