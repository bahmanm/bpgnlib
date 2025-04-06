package com.bahmanm.pgn

import com.bahmanm.pgn.models.Game
import groovy.util.logging.Slf4j
import spock.lang.Specification


@Slf4j
class DeserialiserSpec extends Specification {

  def 'test empty input'() {
    when:
    new Deserialiser(null).nextGame()

    then:
    thrown(IllegalArgumentException)

    when:
    new Deserialiser('').nextGame()

    then:
    thrown(IllegalArgumentException)
  }

  def 'test minimal game'() {
    setup:
    def pgn = '''
            [Event "Test"]
            [Site "Home"]
            [Date "2024.01.26"]
            [Round "?"]
            [White "Me"]
            [Black "You"]
            [Result "*"]

            *
            '''
    def deserialiser = new Deserialiser(pgn)

    when:
    def game = deserialiser.nextGame()

    then:
    game != null
    game.tags.size() == 7

    game.tags.find { it.key == 'Event' }.value == 'Test'
    game.result == '*'
    game.firstPly == null
    game.startingMoveNumber == 1
  }

  def 'test single move game'() {
    setup:
    def pgn = '''
            [Event "Test"]
            [Site "Home"]
            [Date "2024.01.26"]
            [Round "?"]
            [White "Me"]
            [Black "You"]
            [Result "*"]

            1. e4 *
           '''
    def deserialiser = new Deserialiser(pgn)

    when:
    def game = deserialiser.nextGame()

    then:
    game != null
    game.tags.size() == 7
    game.tags.find { it.key == 'Event' }.value == 'Test'
    game.result == '*'
    game.firstPly != null
    game.firstPly.san == 'e4'
    game.firstPly.next == null
    game.startingMoveNumber == 1
  }

  def 'test two move game'() {
    setup:
    def pgn = '''
            [Event "Test"]
            [Site "Home"]
            [Date "2024.01.26"]
            [Round "?"]
            [White "Me"]
            [Black "You"]
            [Result "*"]

            1. e4 c5 *
            '''
    def deserialiser = new Deserialiser(pgn)

    when:
    def game = deserialiser.nextGame()

    then:
    game != null
    game.tags.size() == 7
    game.tags.find { it.key == 'Event' }.value == 'Test'
    game.result == '*'
    game.firstPly != null
    game.firstPly.san == 'e4'
    game.firstPly.next != null
    game.firstPly.next.san == 'c5'
    game.firstPly.next.next == null
    game.startingMoveNumber == 1
  }

  def 'test game with result'() {
    setup:
    def pgn = '''
            [Event "Test"]
            [Site "Home"]
            [Date "2024.01.26"]
            [Round "?"]
            [White "Me"]
            [Black "You"]
            [Result "1-0"]

            1. e4 c5 2. Nf3 d6 1-0
            '''
    def deserialiser = new Deserialiser(pgn)

    when:
    def game = deserialiser.nextGame()

    then:
    game != null
    game.result == '1-0'
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
    def deserialiser = new Deserialiser(pgn)

    when:
    def game = deserialiser.nextGame()

    then:
    game.result == '1/2-1/2'
  }

  def 'test game with comments'() {
    setup:
    def pgn = '''
            [Event "Test"]
            [Site "Home"]
            [Date "2024.01.26"]
            [Round "?"]
            [White "Me"]
            [Black "You"]
            [Result "*"]

            1. e4 {This is a comment} c5 {Another comment} 2. Nf3 d6 *
            '''
    def deserialiser = new Deserialiser(pgn)

    when:
    def game = deserialiser.nextGame()

    then:
    game != null
    def firstPly = game.firstPly
    firstPly.commentAfter == 'This is a comment'
    firstPly.next.commentAfter == 'Another comment'
    game.startingMoveNumber == 1
  }

  def 'test game with variations'() {
    setup:
    def pgn = '''
            [Event "Test"]
            [Site "Home"]
            [Date "2024.01.26"]
            [Round "?"]
            [White "Me"]
            [Black "You"]
            [Result "*"]

            1. e4 (1. d4 e5 2. c4 f5 ) c5 2. Nf3 d6 *
            '''
    def deserialiser = new Deserialiser(pgn)

    when:
    def game = deserialiser.nextGame()

    then:
    game != null
    def firstPly = game.firstPly
    firstPly.san == 'e4'
    firstPly.variations.size() == 1
    firstPly.variations[0].san == 'd4'
    firstPly.next.san == 'c5'
    game.startingMoveNumber == 1
  }

  def 'test game with nested variations'() {
    setup:
    def pgn = '''
            [Event "Test"]
            [Site "Home"]
            [Date "2024.01.26"]
            [Round "?"]
            [White "Me"]
            [Black "You"]
            [Result "*"]

            1. e4 (1. d4 (1. c4)) c5 *
            '''
    def deserialiser = new Deserialiser(pgn)

    when:
    def game = deserialiser.nextGame()

    then:
    game != null
    def firstPly = game.firstPly;
    firstPly.san == 'e4'
    firstPly.variations.size() == 1
    firstPly.variations[0].san == 'd4'
    firstPly.variations[0].variations.size() == 1
    firstPly.variations[0].variations[0].san == 'c4'
    game.startingMoveNumber == 1
  }

  def 'test game with nag'() {
    setup:
    def pgn = '''
            [Event "Test"]
            [Site "Home"]
            [Date "2024.01.26"]
            [Round "?"]
            [White "Me"]
            [Black "You"]
            [Result "*"]

            1. e4 \$1 c5 \$2 2. Nf3 d6 *
            '''
    def deserialiser = new Deserialiser(pgn)

    when:
    def game = deserialiser.nextGame()

    then:
    game != null
    def firstPly = game.firstPly
    firstPly.san == 'e4'
    firstPly.nag == '$1'
    firstPly.next.san == 'c5'
    firstPly.next.nag == '$2'
    game.startingMoveNumber == 1
  }

  def 'test real game'() {
    setup:
    def pgn = '''
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
            '''
    def deserialiser = new Deserialiser(pgn)

    when:
    def game = deserialiser.nextGame()

    then:
    game != null
    game.tags.size() == 11
    game.result == '1/2-1/2'
    game.firstPly.san == 'd4'
    game.firstPly.next.san == 'Nf6'
    game.startingMoveNumber == 1
  }

  def 'test game with missing tags'() {
    setup:
    def pgn = '1. e4 c5 *'
    def deserialiser = new Deserialiser(pgn)

    when:
    def game = deserialiser.nextGame()

    then:
    game != null
    game.tags.size() == 0
    game.firstPly.san == 'e4'
    game.startingMoveNumber == 1
  }

  def 'test game with extra whitespace'() {
    setup:
    def pgn = '''
            [Event  "Test"  ]
            [Site  "Home"  ]

            1.  e4    c5  *
            '''
    def deserialiser = new Deserialiser(pgn)

    when:
    def game = deserialiser.nextGame()

    then:
    game != null
    game.tags.size() == 2
    game.firstPly.san == 'e4'
    game.startingMoveNumber == 1
  }

  def 'test game with only result'() {
    setup:
    def pgn = '1-0'
    def deserialiser = new Deserialiser(pgn)

    when:
    def game = deserialiser.nextGame()

    then:
    game != null
    game.result == '1-0'
    game.firstPly == null
    game.startingMoveNumber == 1
  }

  def 'test game with multiple variations'() {
    setup:
    def pgn = '''
            1. e4 (1. d4) (1. c4) c5 *
            '''
    def deserialiser = new Deserialiser(pgn)

    when:
    def game = deserialiser.nextGame()

    then:
    game != null
    def firstPly = game.firstPly
    firstPly.san == 'e4'
    firstPly.variations.size() == 2
    firstPly.variations[0].san == 'd4'
    firstPly.variations[1].san == 'c4'
    game.startingMoveNumber == 1
  }

  def 'test game with variations and comments'() {
    setup:
    def pgn = '''
            1. e4 (1. d4 {D Queen's Pawn}) c5 {Sicilian} *
            '''
    def deserialiser = new Deserialiser(pgn)

    when:
    def game = deserialiser.nextGame()

    then:
    game != null
    def firstPly = game.firstPly
    firstPly.san == 'e4'
    firstPly.variations.size() == 1
    firstPly.variations[0].san == 'd4'
    firstPly.variations[0].commentAfter == "D Queen's Pawn"
    firstPly.next.san == 'c5'
    firstPly.next.commentAfter == 'Sicilian'
    game.startingMoveNumber == 1
  }

  def 'test game with long NAG'() {
    setup:
    def pgn = '''
            [Event "Test"]
            [Site "Test"]
            [Date "2024.02.09"]
            [Round "1"]
            [White "Player1"]
            [Black "Player2"]
            [Result "*"]
            
            1. e4 $1234 c5 *
            '''
    def deserialiser = new Deserialiser(pgn)

    when:
    def game = deserialiser.nextGame()

    then:
    game != null
    game.firstPly.nag == '$1234'
    game.firstPly.san == 'e4'
    game.startingMoveNumber == 1
  }

  def 'test game with move number with three dots'() {
    setup:
    def pgn = '''
            [Event "Test"]
            [Site "Test"]
            [Date "2024.02.09"]
            [Round "1"]
            [White "Player1"]
            [Black "Player2"]
            [Result "*"]
            
            1... c5 3. Nf3 d6 *
            '''
    def deserialiser = new Deserialiser(pgn)

    when:
    def game = deserialiser.nextGame()

    then:
    game != null
    game.firstPly.san == 'c5'
    game.firstPly.next.san == 'Nf3'
    game.startingMoveNumber == 1
  }

  def 'test game starting from ply 2, white moves first'() {
    setup:
    def pgn = '''
            [Event "Test"]
            [Site "Test"]
            [Date "2024.02.10"]
            [Round "1"]
            [White "Player1"]
            [Black "Player2"]
            [Result "*"]
            [StartPly "2"]
            
            2. e4 c5 *
            '''
    def deserialiser = new Deserialiser(pgn)

    when:
    def game = deserialiser.nextGame()

    then:
    game != null
    game.firstPly.san == 'e4'
    game.firstPly.next.san == 'c5'
    game.startingMoveNumber == 2
  }

  def 'test game starting from ply 2, black moves first'() {
    setup:
    def pgn = '''
            [Event "Test"]
            [Site "Test"]
            [Date "2024.02.10"]
            [Round "1"]
            [White "Player1"]
            [Black "Player2"]
            [Result "*"]
            [StartPly "2"]
            
            2... c5 3. e4 *
            '''
    def deserialiser = new Deserialiser(pgn)

    when:
    def game = deserialiser.nextGame()

    then:
    game != null
    game.firstPly.san == 'c5'
    game.firstPly.next.san == 'e4'
    game.startingMoveNumber == 2
  }

  def 'test multi-game PGN'() {
    given:
    def pgn = '''
            [Event "Casual Game"]
            [Site "Berlin GER"]
            [Date "1852.10.04"]
            [Round "-"]
            [White "Adolf Anderssen"]
            [Black "Jean Dufresne"]
            [Result "1-0"]
            [ECO "C33"]
            [Opening "King's Gambit Accepted: Kieseritzky Gambit"]
            [Variation "Allgaier Gambit"]
            [Annotator "Neil McDonald"]
            [Source "The King's Gambit"]
            [PlyCount "47"]

            1. e4 e5 2. f4 exf4 3. Nf3 g5 4. h4 g4 5. Ne5 h5 6. Bc4 Nh6 7. d4 d6 8. Nd3 Be7
            9. Bxf4 Bxh4+ 10. g3 Bg5 11. Nc3 c6 12. Qd2 Bxf4 13. Nxf4 Ng8 14. O-O-O b5 15. Bb3 a5
            16. a3 Na6 17. e5 d5 18. Ne4 dxe4 19. Nxh5 Rxh5 20. Rxh5 Bxd2+ 21. Rxd2 Qe7 22. Rh7
            f5 23. exf6 Nxf6 24. Rh8+ Ng8 25. Bxg8 Qf6 26. Nxf6+ 1/2-1/2

            [Event "Casual Game"]
            [Site "London ENG"]
            [Date "1851.05.24"]
            [Round "1"]
            [White "Adolf Anderssen"]
            [Black "Lionel Kieseritzky"]
            [Result "1-0"]
            [ECO "C33"]
            [Opening "King's Gambit Accepted, Kieseritzky Gambit"]
            [Variation "Allgaier Gambit"]
            [PlyCount "25"]

            1. e4 e5 2. f4 exf4 3. Nf3 g5 4. h4 g4 5. Ne5 Nf6 6. Bc4 d5 7. exd5 b5 8. Bb3 Nxd5 9. O-O Be6 10. fxe6 fxe6 11. Qxg4 Qe7 12. Rf1 Rg8 13. Qh5+ Kd8 14. Rf7 1-0
            '''
    def deserialiser = new Deserialiser(pgn)
    
    when:
    List<Game> games = []
    def game
    while (game = deserialiser.nextGame()) {
      games << game
    }

    then:
    games.size() == 2
    games[0].result == '1/2-1/2'
    games[0].tags.find { it.key == 'PlyCount' }.value == '47'
    games[1].result == '1-0'
    games[1].tags.find { it.key == 'PlyCount' }.value == '25'
  }
}
