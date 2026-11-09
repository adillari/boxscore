# boxscore

A small service that ingests a basketball play-by-play feed and serves box scores over REST.

Java 8, Spring Boot 2.7, MySQL 8, Flyway, Spock.

## Run it

You only need Docker.

```bash
docker compose up --build
```

The app listens on `localhost:8080`. Flyway creates the schema on startup.

Load the provided game:

```bash
(echo '['; paste -sd, data/events.jsonl; echo ']') | curl -s -X POST \
  -H 'Content-Type: application/json' --data-binary @- \
  localhost:8080/games/G-20261114-FOXES-BEARS/events
```

That returns `{"stored":411,"duplicates":4,"rejected":[]}`. Run it again and you get `{"stored":0,"duplicates":415,"rejected":[]}`.

Then:

```bash
curl -s localhost:8080/games/G-20261114-FOXES-BEARS/boxscore
curl -s localhost:8080/games/G-20261114-FOXES-BEARS/players/P-401
```

Reset everything with `docker compose down -v`.

Tests need Java 8 and Maven: `mvn test`. The Docker build skips them to keep startup quick.

## API

| Method | Path | What it does |
|---|---|---|
| POST | `/games/{id}/events` | Stores one event or an array of events. Idempotent on `eventId`. |
| GET | `/games/{id}/boxscore` | Every player's line, plus team totals. |
| GET | `/games/{id}/players/{playerId}` | One player's line. |

POST responds with counts, so a client can tell new from repeated:

```json
{"stored": 411, "duplicates": 4, "rejected": []}
```

A box score looks like this (trimmed):

```json
{
  "gameId": "G-20261114-FOXES-BEARS",
  "teams": [{
    "teamId": "T-FOXES",
    "name": "Harbor City Foxes",
    "players": [{"playerId": "P-301", "name": "Jordan Vance", "PTS": 7, "FGM": 2, "FGA": 9, "3PM": 1, "3PA": 3, "FTM": 2, "FTA": 5, "REB": 2, "AST": 1, "TO": 0, "PF": 1}],
    "unattributed": [{"playerId": "P-388", "PTS": 0, "FGM": 0, "FGA": 0, "3PM": 0, "3PA": 0, "FTM": 0, "FTA": 0, "REB": 1, "AST": 0, "TO": 0, "PF": 0}],
    "totals": {"PTS": 67, "FGM": 22, "FGA": 53, "3PM": 6, "3PA": 19, "FTM": 17, "FTA": 27, "REB": 37, "AST": 13, "TO": 12, "PF": 16}
  }],
  "warnings": ["Player P-388 is not on the roster of T-FOXES"]
}
```

An unknown game or player returns 404.

## Design

**Store every event as it arrived. Compute the box score from scratch on each read.**

```
POST -> validate -> insert if eventId is new -> events table
GET  -> load the game's events -> BoxScoreCalculator -> add roster names -> JSON
```

The `events` table is an append-only log with `event_id` as its primary key. `BoxScoreCalculator` is a plain class with no Spring and no database: a list of events goes in, stat lines come out. It sorts by `seq`, drops repeated ids, applies corrections, then counts.

I chose this over keeping running totals because the feed has corrections. With running totals, a late correction means working out how to undo an earlier increment. With replay, the original is never touched and the correction is just one more event in the log.

Idempotency comes from the database, not from application code:

```sql
INSERT INTO events (...) VALUES (...)
ON DUPLICATE KEY UPDATE event_id = event_id
```

A repeat is a no-op, and two identical requests racing each other are settled by the primary key. `useAffectedRows=true` on the JDBC URL makes MySQL report 0 for a repeat, which is how the response counts duplicates. I avoided `INSERT IGNORE` because it also hides real errors such as truncated data.

## Edge cases in the data

The file has 415 lines and 411 unique events.

| Issue | Where | What I did |
|---|---|---|
| Exact duplicates | 4 events (seq 348, 352, 366, 374) | Primary key at ingest. The calculator dedupes too, so it is correct on its own. |
| Out-of-order delivery | 37 places where `seq` goes backward | Sort by `seq` on read. |
| Voided made shot | seq 135 voids seq 129 (P-306, 2PT) | Remove the shot entirely: points, make and attempt. |
| Assist on that voided shot | seq 130 (P-305) | Remove the assist too. No basket, no assist. |
| Shot type corrected | seq 248 changes seq 234 (P-401) from 3PT to 2PT | Apply on replay: one point less, and one less 3PM and 3PA. |
| Player corrected | seq 38 moves the rebound at seq 29 from P-402 to P-401 | Apply on replay. |
| Player not on the roster | seq 331, `P-388`, a Foxes rebound | Count it in the team total, list it under `unattributed`, and add a warning. |

Final score: **Foxes 67, Bears 66**. Without the corrections it would be 69 to 67.

`P-388` looks like a typo for `P-308`, but the substitution events show P-308 was on the bench at that point, so I did not guess.

## Assumptions

- I built against the files I was given. They differ from the brief, which describes 438 lines and uses a Hawks vs Owls game in its example. My file has 415 lines for Foxes vs Bears.
- A void means the event never happened for stats. The void's reason is `OFFENSIVE_FOUL`, but the feed sends no foul or turnover for it, so I did not invent one.
- If the same `eventId` arrives with different content, the first write wins. All four duplicates here were identical.
- A correction that targets an unknown event is ignored with a warning.
- In a batch, good events are stored and bad ones are listed in `rejected`. An event is rejected if it lacks `eventId`, `seq` or `type`, or if its `gameId` differs from the URL.
- Free throws carry `attempt` and `of` fields that the brief does not mention. The box score does not need them.

## Tradeoffs

- **Compute on read.** Every GET replays the whole game. That is about 400 events, so it is cheap, but it would not suit heavy read traffic.
- **JSON payload column.** Each event is stored as JSON, so using another field of the feed needs no migration. The cost is that ad hoc SQL over stats is awkward. The payload is the parsed event written back out, not the raw request body, so a key the `Event` class does not know would be dropped.
- **Roster in a file.** `src/main/resources/roster.json` is packed into the jar. It only covers this one game, and any other game id returns 404.
- **JdbcTemplate, not JPA.** There is one table and two queries, and I wanted the idempotent insert to be visible.
- **Public fields, no getters.** `Event`, `PlayerLine` and `Roster` only carry data. Getters and setters would triple their size and add no behavior.
- **No integration test.** The specs cover the calculator, including the full provided feed, and the roster logic in the service using a stubbed repository. The API and database were checked by hand with the commands above.

## With more time

1. Integration tests against real MySQL with Testcontainers.
2. Games and players in tables, not a roster file.
3. An alert when a repeated `eventId` has different content.
4. A stored box score that is updated as events arrive, once read volume needs it.
5. Flag impossible states, such as a player continuing after fouling out.
6. Metrics on rejected events and warnings, plus auth and API versioning.

## Tooling Disclosure

I built this with Claude Code. I defined the stack and structure, it wrote most of the implementation, and I reviewed each commit and verified the results against the data.
