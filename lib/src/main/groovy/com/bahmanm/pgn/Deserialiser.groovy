package com.bahmanm.pgn


import com.bahmanm.pgn.models.*
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import static java.io.StreamTokenizer.TT_EOF
import static java.io.StreamTokenizer.TT_NUMBER
import static java.io.StreamTokenizer.TT_WORD

@Slf4j
@CompileStatic
class Deserialiser {

  private final static Integer PAREN_OPEN = '('.codePointAt(0)
  private final static Integer PAREN_CLOSE = ')'.codePointAt(0)
  private final static Integer CURLY_OPEN = '{'.codePointAt(0)
  private final static Integer CURLY_CLOSE = '}'.codePointAt(0)
  private final static Integer BRACK_OPEN = '['.codePointAt(0)
  private final static Integer BRACK_CLOSE = ']'.codePointAt(0)
  private final static Integer QUOTE_DOUBLE = '"'.codePointAt(0)
  private final static Integer SIGIL = '$'.codePointAt(0)
  private final static Integer DOT = '.'.codePointAt(0)

  private StreamTokenizer tokenizer
  private Integer currentToken = null

  Deserialiser(String pgn) {
    if (!pgn || pgn.empty) {
      throw new IllegalArgumentException("PGN string cannot be null")
    }
    def reader = new StringReader(pgn)
    tokenizer = new StreamTokenizer(reader)
  }

  private void configureTokeniser(Boolean isParseNumbers) {
    tokenizer.resetSyntax()
    tokenizer.wordChars('!'.codePointAt(0), '~'.codePointAt(0))
    tokenizer.whitespaceChars('\n'.codePointAt(0), ' '.codePointAt(0))
    tokenizer.quoteChar(QUOTE_DOUBLE)
    tokenizer.ordinaryChar(BRACK_OPEN)
    tokenizer.ordinaryChar(BRACK_CLOSE)
    tokenizer.ordinaryChar(PAREN_OPEN)
    tokenizer.ordinaryChar(PAREN_CLOSE)
    tokenizer.ordinaryChar('.'.codePointAt(0))
    if (isParseNumbers) {
      tokenizer.parseNumbers()
    }
    tokenizer.eolIsSignificant(false)
  }

  private Integer readNextToken() {
    currentToken = tokenizer.nextToken()
    return currentToken
  }

  private Integer getCurrentToken() {
    return currentToken
  }

  Game nextGame() {
    if (currentToken == TT_EOF) {
      return null
    } else {
      return deserialise()
    }
  }

  /**
   * Deserializes a PGN string into a Game object.
   *
   * @param pgn The PGN data as a String.
   * @return A Game object representing the parsed PGN data, or null if parsing fails.
   * @throws IllegalArgumentException if the input String is null or empty
   */
  private Game deserialise() {
    try {
      configureTokeniser(true)
      def tags = readTags()
      def startingMoveNumber = readStartingMoveNumber()
      configureTokeniser(false)
      def moveText = readGameMovesText()
      def result = parseResult(moveText)
      def startingSide = parseStartingSide(moveText)
      def firstPly = parsePlies(moveText)
      if (!tags && !result && !moveText) {
        return null
      } else {
        return new Game(
            tags: tags, startingMoveNumber: startingMoveNumber, 
            startingSide: startingSide, firstPly: firstPly, result: result ?: '*')
      }
    } catch (IOException e) {
      log.error("Error during deserialization: ${e.message}", e)
      throw new RuntimeException(e)
    }
  }

  private List<Tag> readTags() {
    def result = [] as List<Tag>
    while (true) {
      readNextToken()
      if (currentToken == BRACK_OPEN) {
        readNextToken()
        String key = tokenizer.sval
        if (!key) {
          break
        }
        if (readNextToken() != QUOTE_DOUBLE) {
          tokenizer.pushBack()
          break
        }
        def value = tokenizer.sval
        if (readNextToken() != BRACK_CLOSE) {
          tokenizer.pushBack()
          break
        }
        result << new Tag(key, value)
      } else {
        tokenizer.pushBack()
        break
      }
    }
    return result
  }

  private Integer readStartingMoveNumber() {
    tokenizer.pushBack()

    def result = -1
    while (readNextToken() != TT_EOF) {
      if (currentToken == TT_NUMBER) {
        result = tokenizer.nval.intValue()
        break
      } else if (currentToken == TT_WORD && tokenizer.sval =~ /^[1-9][0-9]*\..*$/) { // TODO Unreachable
        result = tokenizer.sval.split(/\./)[0] as Integer
        break
      }
    }
    tokenizer.pushBack()
    return result > 0 ? result : 1
  }

  private String parseResult(String moveText) {
    if (moveText =~ /.*1 ?-0$/) {
      return '1-0'
    } else if (moveText =~ /.*0 ?-1$/) {
      return '0-1'
    } else if (moveText =~ /.*1 ?\/2-1\/2$/) {
      return '1/2-1/2'
    } else if (moveText =~ /.*\*$/) {
      return '*'
    } else {
      return null
    }
  }

  private Game.Side parseStartingSide(String moveText) {
    return moveText.find(/[1-9]\d*\.+/)?.contains('..') ? Game.Side.BLACK : Game.Side.WHITE
  }

  private void skipTokensUntilFirstMove() {
    def found = false

    while (!found && readNextToken() != TT_EOF) {
      if (currentToken == TT_NUMBER) {
        found = true
      } else if (currentToken == TT_WORD) { // TODO Unreachable
        if (tokenizer.sval =~ /^[1-9][0-9]*\..*$/) {
          found = true
        } else if (tokenizer.sval in ['1-0', '0-1', '1/2-1/2', '*']) {
          found = true
        }
      } else if (currentToken == BRACK_OPEN) {
        found = true
      }
    }
    if (found) {
      tokenizer.pushBack()
    }
  }

  private String readGameMovesText() {
    skipTokensUntilFirstMove()

    def result = ''
    while (readNextToken() != TT_EOF) {
      if (currentToken == PAREN_OPEN) {
        result += ' ( '
      } else if (currentToken == PAREN_CLOSE) {
        result += ' ) '
      } else if (currentToken == TT_NUMBER) {
        if (!result.endsWith(' ')) {
          result += ' '
        }
        result += "${tokenizer.nval.intValue()}"
      } else if (currentToken == TT_WORD) {
        if (!result.endsWith(' ')) {
          result += ' '
        }
        result += "${tokenizer.sval}"
      } else {
        result += "${Character.toString(currentToken)}"
      }

      if (result =~ /(1-0|0-1|\*|1\/2-1\/2)\s*$/) {
        break
      }
    }
    return result.trim()
  }

  private List<Map<String, String>> getIntermediateElements(String movesText) {
    if (movesText =~ /^\s*\(.+\)\s*$/) {
      movesText = movesText.substring(1, movesText.size() - 1)
    }

    def result = [] as List<Map<String, String>>
    def parenCount = 0
    def currentMove = ''
    def inComment = false
    def currentComment = ''
    def inNag = false
    def currentNag = ''

    for (def s : movesText) {
      if (PAREN_OPEN == s) {
        parenCount += 1
        if (!currentMove.empty) {
          result << [move: currentMove.trim()]
          currentMove = ''
        }
        result << [openParen: '(']
      } else if (PAREN_CLOSE == s) {
        parenCount -= 1
        if (!currentMove.empty) {
          result << [move: currentMove.trim()]
        }
        result << [closeParen: ')']
        currentMove = ''
      } else if (CURLY_OPEN == s) {
        inComment = true
      } else if (inComment) {
        if (CURLY_CLOSE == s) {
          result[-1]['comment'] = currentComment.trim()
          inComment = false
          currentComment = ''
        } else {
          currentComment += s
        }
      } else if (SIGIL == s) {
        inNag = true
        currentNag = '$'
      } else if (inNag) {
        if (s =~ /\d/) {
          currentNag += s
        } else {
          if (!result.empty) {
            result[-1]['nag'] = currentNag.trim()
          }
          inNag = false
          currentNag = ''
        }
      } else if (s =~ /\s+/) {
        if (!currentMove.empty) {
          if (!(currentMove =~ /^\s*\d+\s*$/) && !(currentMove =~ /^\*$/)) {
            result << [move: currentMove.trim()]
          }
          currentMove = ''
        }
      } else {
        currentMove += s
      }
    }
    if (!currentMove.empty && !currentMove =~ /^\d*$/ && !currentMove =~ /^\*$/) {
      result << [move: currentMove.trim()]
    }
    return result
  }

  private Ply parsePlies(String movesText) {
    def firstPly = null as Ply
    def prevPly = null as Ply
    def currentVariation = ''
    def inVariation = false
    def variationDepth = 0
    for (def element : getIntermediateElements(movesText)) {
      if (!element.move) {
        if (element.openParen && prevPly) {
          inVariation = true
          currentVariation += '( '
          variationDepth += 1
        } else if (element.closeParen) {
          currentVariation += ' ) '
          variationDepth -= 1
          inVariation = variationDepth != 0
          if (!inVariation) {
            def variationPly = parsePlies(currentVariation.trim())
            prevPly.variations << variationPly
            currentVariation = ''
          }
        }
      } else if (inVariation) {
        currentVariation += "${element.move} "
        if (element.comment) {
          currentVariation += "{ ${element.comment} } "
        }
      } else if (!(element.move =~ /\s*[1-9][0-9]*\.\s*/)) {
        def ply = new Ply(san: element.move, prev: prevPly, commentAfter: element.comment, nag: element.nag)
        firstPly = firstPly ?: ply
        prevPly?.next = ply
        prevPly = ply
      }
    }
    return firstPly
  }
}
