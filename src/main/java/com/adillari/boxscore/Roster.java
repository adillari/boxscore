package com.adillari.boxscore;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Roster {
    public String gameId;
    public List<Team> teams;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Team {
        public String teamId;
        public String name;
        public List<Player> players;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Player {
        public String playerId;
        public String name;
    }
}
