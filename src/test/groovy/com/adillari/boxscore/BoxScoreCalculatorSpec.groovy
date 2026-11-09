package com.adillari.boxscore

import spock.lang.Specification

class BoxScoreCalculatorSpec extends Specification {
    def calculator = new BoxScoreCalculator()

    private static Event shot(String id, int seq, String player = 'P-1', String type = '2PT') {
        new Event(eventId: id, seq: seq, type: 'SHOT_MADE', teamId: 'T', playerId: player, shotType: type)
    }

    private static Event assist(String id, int seq, String shotId, String player = 'P-2') {
        new Event(eventId: id, seq: seq, type: 'ASSIST', teamId: 'T', playerId: player, refEventId: shotId)
    }

    private static Event correction(String id, int seq, String target, Map body) {
        new Event(eventId: id, seq: seq, type: 'CORRECTION', refEventId: target,
                  correction: new Event.Correction(body))
    }

    def "scores made shots by type"() {
        when:
        def r = calculator.calculate([shot('a', 1, 'P-1', '3PT'), shot('b', 2, 'P-1', '2PT')])

        then:
        with(r.lines['P-1']) { pts == 5; fgm == 2; fga == 2; threePm == 1; threePa == 1 }
    }

    def "counts misses, free throws and the other stats"() {
        given:
        def event = { String id, int seq, String type, String shotType = null ->
            new Event(eventId: id, seq: seq, type: type, teamId: 'T', playerId: 'P-1', shotType: shotType)
        }

        when:
        def r = calculator.calculate([
            event('a', 1, 'SHOT_MISSED', '3PT'), event('b', 2, 'SHOT_MISSED', '2PT'),
            event('c', 3, 'FREE_THROW_MADE'), event('d', 4, 'FREE_THROW_MISSED'),
            event('e', 5, 'REBOUND'), event('f', 6, 'TURNOVER'), event('g', 7, 'FOUL')])

        then:
        with(r.lines['P-1']) {
            pts == 1; fgm == 0; fga == 2; threePm == 0; threePa == 1
            ftm == 1; fta == 2; reb == 1; to == 1; pf == 1
        }
    }

    def "a duplicate eventId counts once"() {
        expect:
        calculator.calculate([shot('a', 1), shot('a', 1)]).lines['P-1'].pts == 2
    }

    def "the result does not depend on arrival order"() {
        given:
        def events = [shot('a', 1, 'P-1', '3PT'),
                      correction('c', 2, 'a', [action: 'UPDATE', fields: [shotType: '2PT']])]

        expect:
        calculator.calculate(events).lines['P-1'].pts == 2
        calculator.calculate(events.reverse()).lines['P-1'].pts == 2
    }

    def "voiding a shot removes the shot and its assist"() {
        when:
        def r = calculator.calculate([shot('a', 1), assist('b', 2, 'a'),
                                      correction('c', 3, 'a', [action: 'VOID', reason: 'OFFENSIVE_FOUL'])])

        then:
        r.lines['P-1'] == null
        r.lines['P-2'] == null
    }

    def "an UPDATE moves the stat to the corrected player"() {
        given:
        def rebound = new Event(eventId: 'a', seq: 1, type: 'REBOUND', teamId: 'T', playerId: 'P-1')

        when:
        def r = calculator.calculate([rebound, correction('c', 2, 'a', [action: 'UPDATE', fields: [playerId: 'P-9']])])

        then:
        r.lines['P-1'] == null
        r.lines['P-9'].reb == 1
    }

    def "an UPDATE from 3PT to 2PT drops a point and the three-point stats"() {
        when:
        def r = calculator.calculate([shot('a', 1, 'P-1', '3PT'),
                                      correction('c', 2, 'a', [action: 'UPDATE', fields: [shotType: '2PT']])])

        then:
        with(r.lines['P-1']) { pts == 2; fgm == 1; fga == 1; threePm == 0; threePa == 0 }
    }

    def "a correction with an unknown target warns and changes nothing"() {
        when:
        def r = calculator.calculate([shot('a', 1), correction('c', 2, 'nope', [action: 'VOID'])])

        then:
        r.lines['P-1'].pts == 2
        r.warnings.size() == 1
    }

    def "an assist on an unknown shot is not counted"() {
        when:
        def r = calculator.calculate([assist('b', 1, 'nope')])

        then:
        r.lines['P-2'] == null
        r.warnings.size() == 1
    }
}
