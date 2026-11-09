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

    void add(PlayerLine o) {
        pts += o.pts; fgm += o.fgm; fga += o.fga; threePm += o.threePm; threePa += o.threePa;
        ftm += o.ftm; fta += o.fta; reb += o.reb; ast += o.ast; to += o.to; pf += o.pf;
    }
}
