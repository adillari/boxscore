package com.adillari.boxscore;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One row of a box score, for a player or a team total. */
public class PlayerLine {
    public String playerId;
    public String name;
    @JsonProperty("PTS") public int pts;
    @JsonProperty("FGM") public int fgm;
    @JsonProperty("FGA") public int fga;
    @JsonProperty("3PM") public int threePm;
    @JsonProperty("3PA") public int threePa;
    @JsonProperty("FTM") public int ftm;
    @JsonProperty("FTA") public int fta;
    @JsonProperty("REB") public int reb;
    @JsonProperty("AST") public int ast;
    @JsonProperty("TO") public int to;
    @JsonProperty("PF") public int pf;

    /** Adds another line's numbers into this one. Team totals are built this way. */
    void add(PlayerLine other) {
        pts += other.pts;
        fgm += other.fgm;
        fga += other.fga;
        threePm += other.threePm;
        threePa += other.threePa;
        ftm += other.ftm;
        fta += other.fta;
        reb += other.reb;
        ast += other.ast;
        to += other.to;
        pf += other.pf;
    }
}
