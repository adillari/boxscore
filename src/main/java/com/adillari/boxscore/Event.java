package com.adillari.boxscore;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/** One line of the play-by-play feed. Optional values use wrapper types so they can be null. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Event {
    public String eventId;
    public String gameId;
    public Integer seq;
    public String timestamp;
    public Integer period;
    public String clock;
    public String type;
    public String teamId;
    public String playerId;
    public String shotType;
    public Boolean offensive;
    public String foulType;
    public String refEventId;
    public Integer attempt;
    public Integer of;
    public Correction correction;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Correction {
        public String action;
        public Map<String, Object> fields;
        public String reason;
    }

    /**
     * Copy of this event with an UPDATE correction's fields laid on top.
     * The original is never changed. Only teamId, playerId and shotType can be corrected.
     */
    Event withFields(Map<String, Object> fields) {
        Event copy = new Event();
        copy.eventId = eventId;
        copy.gameId = gameId;
        copy.seq = seq;
        copy.timestamp = timestamp;
        copy.period = period;
        copy.clock = clock;
        copy.type = type;
        copy.offensive = offensive;
        copy.foulType = foulType;
        copy.refEventId = refEventId;
        copy.attempt = attempt;
        copy.of = of;
        copy.correction = correction;
        copy.teamId = fields.containsKey("teamId") ? (String) fields.get("teamId") : teamId;
        copy.playerId = fields.containsKey("playerId") ? (String) fields.get("playerId") : playerId;
        copy.shotType = fields.containsKey("shotType") ? (String) fields.get("shotType") : shotType;
        return copy;
    }
}
