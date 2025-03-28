package com.bahmanm.pgn

import groovy.util.logging.Slf4j
import spock.lang.Specification

import com.bahmanm.pgn.models.*

@Slf4j
class DeserialiserSpec extends Specification {

    def "test empty input"() {
        setup:
        Deserialiser deserialiser = new Deserialiser()

        when:
        deserialiser.deserialise(null)

        then:
        thrown(IllegalArgumentException)

        when:
        deserialiser.deserialise("")

        then:
        thrown(IllegalArgumentException)
    }

    def "test minimal game"() {
        setup:
        String pgn = """
            [Event "Test"]
            [Site "Home"]
            [Date "2024.01.26"]
            [Round "?"]
            [White "Me"]
            [Black "You"]
            [Result "*"]

            *
            """
        Deserialiser deserialiser = new Deserialiser()

        when:
        Game game = deserialiser.deserialise(pgn)

        then:
        game != null
        game.tags.size() == 7
        
        game.tags.find { it.key == "Event" }.value == "Test"
        game.result == "*"
        game.firstPly == null
        game.startingMoveNumber == 1
    }

    def "test single move game"() {
        setup:
        String pgn = """
            [Event "Test"]
            [Site "Home"]
            [Date "2024.01.26"]
            [Round "?"]
            [White "Me"]
            [Black "You"]
            [Result "*"]

            1. e4 *
            """
        Deserialiser deserialiser = new Deserialiser()

        when:
        Game game = deserialiser.deserialise(pgn)

        then:
        game != null
        game.tags.size() == 7
        game.tags.find { it.key == "Event" }.value == "Test"
        game.result == "*"
        game.firstPly != null
        game.firstPly.san == "e4"
        game.firstPly.next == null
        game.startingMoveNumber == 1
    }

    def "test two move game"() {
        setup:
        String pgn = """
            [Event "Test"]
            [Site "Home"]
            [Date "2024.01.26"]
            [Round "?"]
            [White "Me"]
            [Black "You"]
            [Result "*"]

            1. e4 c5 *
            """
        Deserialiser deserialiser = new Deserialiser()

        when:
        Game game = deserialiser.deserialise(pgn)

        then:
        game != null
        game.tags.size() == 7
        game.tags.find { it.key == "Event" }.value == "Test"
        game.result == "*"
        game.firstPly != null
        game.firstPly.san == "e4"
        game.firstPly.next != null
        game.firstPly.next.san == "c5"
        game.firstPly.next.next == null
        game.startingMoveNumber == 1
    }

    def "test game with result"() {
        setup:
        String pgn = """
            [Event "Test"]
            [Site "Home"]
            [Date "2024.01.26"]
            [Round "?"]
            [White "Me"]
            [Black "You"]
            [Result "1-0"]

            1. e4 c5 2. Nf3 d6 1-0
            """
        Deserialiser deserialiser = new Deserialiser()

        when:
        Game game = deserialiser.deserialise(pgn)

        then:
        game != null
        game.result == "1-0"
        game.startingMoveNumber == 1
    }

    def "should parse draw result"() {
        given:
        def pgn = '''
[Event "Test Game"]
[Site "Home"]
[Date "2024.01.25"]
[Round "-"]
[White "Player A"]
[Black "Player B"]
[Result "1/2-1/2"]

1. e4 c5 2. Nf3 d6 1/2-1/2
    '''
        def deserialiser = new Deserialiser()

        when:
        def game = deserialiser.deserialise(pgn)

        then:
        game.result == "1/2-1/2"
    }    

    def "test game with comments"() {
        setup:
        String pgn = """
            [Event "Test"]
            [Site "Home"]
            [Date "2024.01.26"]
            [Round "?"]
            [White "Me"]
            [Black "You"]
            [Result "*"]

            1. e4 {This is a comment} c5 {Another comment} 2. Nf3 d6 *
            """
        Deserialiser deserialiser = new Deserialiser()

        when:
        Game game = deserialiser.deserialise(pgn)

        then:
        game != null
        Ply firstPly = game.firstPly
        firstPly.commentAfter == "This is a comment"
        firstPly.next.commentAfter == "Another comment"
        game.startingMoveNumber == 1
    }

    def "test game with variations"() {
        setup:
        String pgn = """
            [Event "Test"]
            [Site "Home"]
            [Date "2024.01.26"]
            [Round "?"]
            [White "Me"]
            [Black "You"]
            [Result "*"]

            1. e4 (1. d4 e5 2. c4 f5 ) c5 2. Nf3 d6 *
            """
        Deserialiser deserialiser = new Deserialiser()

        when:
        Game game = deserialiser.deserialise(pgn)

        then:
        game != null
        Ply firstPly = game.firstPly
        firstPly.san == "e4"
        firstPly.variations.size() == 1
        firstPly.variations[0].san == "d4"
        game.firstPly.next.san == "c5"
        game.startingMoveNumber == 1
    }

    def "test game with nested variations"() {
        setup:
        String pgn = """
            [Event "Test"]
            [Site "Home"]
            [Date "2024.01.26"]
            [Round "?"]
            [White "Me"]
            [Black "You"]
            [Result "*"]

            1. e4 (1. d4 (1. c4)) c5 *
            """
        Deserialiser deserialiser = new Deserialiser()

        when:
        Game game = deserialiser.deserialise(pgn)

        then:
        game != null
        Ply firstPly = game.firstPly;
        firstPly.san == "e4";
        firstPly.variations.size() == 1;
        firstPly.variations[0].san == "d4";
        firstPly.variations[0].variations.size() == 1;
        firstPly.variations[0].variations[0].san == "c4";
        game.startingMoveNumber == 1
    }

    def "test game with nag"() {
        setup:
        String pgn = """
            [Event "Test"]
            [Site "Home"]
            [Date "2024.01.26"]
            [Round "?"]
            [White "Me"]
            [Black "You"]
            [Result "*"]

            1. e4 \$1 c5 \$2 2. Nf3 d6 *
            """
        Deserialiser deserialiser = new Deserialiser()

        when:
        Game game = deserialiser.deserialise(pgn)

        then:
        game != null
        Ply firstPly = game.firstPly
        firstPly.san == "e4"
        firstPly.nag == "\$1"
        game.firstPly.next.san == "c5"
        game.firstPly.next.nag == "\$2"
        game.startingMoveNumber == 1
    }

    def "test real game"() {
        setup:
        String pgn = """
            [Event "FIDE World Championship Match 2023"]
            [Site "Astana KAZ"]
            [Date "2023.04.09"]
            [Round "1"]
            [White "Ding Liren"]
            [Black "Nepomniachtchi, Ian"]
            [Result "1/2-1/2"]
            [ECO "D37"]
            [WhiteElo "2788"]
            [BlackElo "2795"]
            [PlyCount "90"]

            1. d4 Nf6 2. c4 e6 3. Nf3 d5 4. Nc3 Be7 5. Bf4 O-O 6. e3 c5 7. dxc5 Bxc5 8. a3 Nc6 9. Qc2 Re8 10. Be2 h6 11. O-O a6 12. h3 Ba7 13. Rfd1 Qe7 14. cxd5 exd5 15. b4 Be6 16. Rac1 Rac8 17. Qb1 Bb8 18. Bxb8 Rxb8 19. b5 axb5 20. Nxb5 Ne4 21. Qb2 Rec8 22. Nfd4 Qf6 23. Bf3 Ne5 24. Bxe4 dxe4 25. Nxe6 fxe6 26. Rxc8+ Rxc8 27. Nd4 Nd3 28. Qa2 Re8 29. a4 Kh8 30. a5 Rf8 31. Rf1 Re8 32. Qc4 Qg6 33. Qc7 e5 34. Nb5 Qe6 35. Nd6 Rf8 36. Nxe4 Qd5 37. Ng3 b5 38. axb6 Qb3 39. Ne4 Qd5 40. Ng3 Qb3 41. Ne4 Qd5 42. Qc2 Nb4 43. Qb1 Nc6 44. Rd1 Qc4 45. Qd3 Qa4 1/2-1/2
            """
        Deserialiser deserialiser = new Deserialiser()

        when:
        Game game = deserialiser.deserialise(pgn)

        then:
        game != null
        game.tags.size() == 11
        game.result == "1/2-1/2"
        game.firstPly.san == "d4"
        game.firstPly.next.san == "Nf6"
        game.startingMoveNumber == 1
    }

    def "test game with missing tags"() {
        setup:
        String pgn = "1. e4 c5 *"
        Deserialiser deserialiser = new Deserialiser()

        when:
        Game game = deserialiser.deserialise(pgn)

        then:
        game != null
        game.tags.size() == 0
        game.firstPly.san == "e4"
        game.startingMoveNumber == 1
    }

    def "test game with extra whitespace"() {
        setup:
        String pgn = """
            [Event  "Test"  ]
            [Site  "Home"  ]

            1.  e4    c5  *
            """
        Deserialiser deserialiser = new Deserialiser()

        when:
        Game game = deserialiser.deserialise(pgn)

        then:
        game != null
        game.tags.size() == 2
        game.firstPly.san == "e4"
        game.startingMoveNumber == 1
    }

    def "test game with only result"() {
        setup:
        String pgn = "1-0"
        Deserialiser deserialiser = new Deserialiser()

        when:
        Game game = deserialiser.deserialise(pgn)

        then:
        game != null
        game.result == "1-0"
        game.firstPly == null
        game.startingMoveNumber == 1
    }

    def "test game with multiple variations"() {
        setup:
        String pgn = """
            1. e4 (1. d4) (1. c4) c5 *
            """
        Deserialiser deserialiser = new Deserialiser()

        when:
        Game game = deserialiser.deserialise(pgn)

        then:
        game != null
        Ply firstPly = game.firstPly
        firstPly.san == "e4"
        firstPly.variations.size() == 2
        firstPly.variations[0].san == "d4"
        firstPly.variations[1].san == "c4"
        game.startingMoveNumber == 1
    }

    def "test game with variations and comments"() {
        setup:
        String pgn = """
            1. e4 (1. d4 {D Queen's Pawn}) c5 {Sicilian} *
            """
        Deserialiser deserialiser = new Deserialiser()

        when:
        Game game = deserialiser.deserialise(pgn)

        then:
        game != null
        Ply firstPly = game.firstPly
        firstPly.san == "e4"
        firstPly.variations.size() == 1
        firstPly.variations[0].san == "d4"
        firstPly.variations[0].commentBefore == "D Queen's Pawn"
        game.firstPly.next.san == "c5"
        game.firstPly.next.commentBefore == "Sicilian"
        game.startingMoveNumber == 1
    }

    def "test game with long NAG"() {
        setup:
        String pgn = """
            [Event "Test"]
            [Site "Test"]
            [Date "2024.02.09"]
            [Round "1"]
            [White "Player1"]
            [Black "Player2"]
            [Result "*"]
            
            1. e4 \$1234 c5 *
            """
        Deserialiser deserialiser = new Deserialiser()

        when:
        Game game = deserialiser.deserialise(pgn)

        then:
        game != null
        game.firstPly.nag == "\$1234"
        game.firstPly.san == "e4"
        game.startingMoveNumber == 1
    }

    def "test game with move number with three dots"() {
        setup:
        String pgn = """
            [Event "Test"]
            [Site "Test"]
            [Date "2024.02.09"]
            [Round "1"]
            [White "Player1"]
            [Black "Player2"]
            [Result "*"]
            
            1... c5 2. Nf3 d6 *
            """
        Deserialiser deserialiser = new Deserialiser()

        when:
        Game game = deserialiser.deserialise(pgn)

        then:
        game != null
        game.firstPly.san == "c5"
        game.firstPly.next.san == "Nf3"
        game.startingMoveNumber == 1
    }

    def "test game starting from ply 2, white moves first"() {
        setup:
        String pgn = """
            [Event "Test"]
            [Site "Test"]
            [Date "2024.02.10"]
            [Round "1"]
            [White "Player1"]
            [Black "Player2"]
            [Result "*"]
            [StartPly "2"]
            
            2. e4 c5 *
            """
        Deserialiser deserialiser = new Deserialiser()

        when:
        Game game = deserialiser.deserialise(pgn)

        then:
        game != null
        game.firstPly.san == "e4"
        game.firstPly.next.san == "c5"
        game.startingMoveNumber == 2
    }

    def "test game starting from ply 2, black moves first"() {
        setup:
        String pgn = """
            [Event "Test"]
            [Site "Test"]
            [Date "2024.02.10"]
            [Round "1"]
            [White "Player1"]
            [Black "Player2"]
            [Result "*"]
            [StartPly "2"]
            
            2... c5 3. e4 *
            """
        Deserialiser deserialiser = new Deserialiser()

        when:
        Game game = deserialiser.deserialise(pgn)

        then:
        game != null
        game.firstPly.san == "c5"
        game.firstPly.next.san == "e4"
        game.startingMoveNumber == 2
    }
}
