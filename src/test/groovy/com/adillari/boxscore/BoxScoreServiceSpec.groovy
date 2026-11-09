package com.adillari.boxscore

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification

class BoxScoreServiceSpec extends Specification {
    static final GAME = 'G-20261114-FOXES-BEARS'

    // A stub stands in for the repository, so no database is needed.
    private BoxScoreService serviceWith(List<Event> stored) {
        def repository = Stub(EventRepository) { findByGame(_) >> stored }
        new BoxScoreService(repository, new ObjectMapper())
    }

    private static Event rebound(String id, String teamId, String playerId) {
        new Event(eventId: id, seq: 1, type: 'REBOUND', teamId: teamId, playerId: playerId)
    }

    def "a rostered player with no events still gets a line of zeroes"() {
        when:
        def foxes = serviceWith([]).boxScore(GAME).teams.find { it.teamId == 'T-FOXES' }

        then:
        foxes.players.size() == 10
        foxes.players.every { it.pts == 0 && it.name != null }
    }

    def "a player missing from the roster counts for the team but is listed apart"() {
        when:
        def box = serviceWith([rebound('a', 'T-FOXES', 'P-388')]).boxScore(GAME)
        def foxes = box.teams.find { it.teamId == 'T-FOXES' }

        then:
        foxes.unattributed*.playerId == ['P-388']
        foxes.players*.playerId.contains('P-388') == false
        foxes.totals.reb == 1
        box.warnings.size() == 1
    }
}
