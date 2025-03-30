package com.bahmanm.pgn

import groovy.transform.Canonical

import com.bahmanm.pgn.models.*
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.util.regex.Matcher
import java.util.regex.Pattern

import static java.io.StreamTokenizer.TT_EOF
import static java.io.StreamTokenizer.TT_EOL
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

  /**
   * Deserializes a PGN string from a  into a Game object.
   *
   * @param pgn The PGN data as a String.
   * @return A Game object representing the parsed PGN data, or null if parsing fails.
   * @throws IllegalArgumentException if the input String is null
   */
  Game deserialise(String pgn) {
    if (!pgn || pgn.empty) {
      throw new IllegalArgumentException("PGN string cannot be null")
    }

    try {
      StringReader reader = new StringReader(pgn)
      
      StreamTokenizer tokenizer = new StreamTokenizer(reader)
      tokenizer.resetSyntax()
      tokenizer.wordChars('!'.codePointAt(0), '~'.codePointAt(0))
      tokenizer.whitespaceChars('\n'.codePointAt(0), ' '.codePointAt(0))
      tokenizer.quoteChar(QUOTE_DOUBLE)
      tokenizer.ordinaryChar(BRACK_OPEN)
      tokenizer.ordinaryChar(BRACK_CLOSE)
      tokenizer.ordinaryChar(PAREN_OPEN)
      tokenizer.ordinaryChar(PAREN_CLOSE)
      tokenizer.parseNumbers() 
      tokenizer.eolIsSignificant(false)

      def tags = parseTags(tokenizer)
      def startingMoveNumber = parseStartingMoveNumber(tokenizer)
      def moveText = parseMoveText(tokenizer) // Get move text as a string
      def result = parseResult(moveText)
      def firstPly = parseMoveTextToPlies(moveText, tokenizer) // *Process the move text string*
      return new Game(tags, startingMoveNumber, firstPly, result)
    } catch (IOException e) {
      log.error("Error during deserialization: ${e.message}", e)
      throw new RuntimeException(e)
    }
  }

  private List<Tag> parseTags(StreamTokenizer tokenizer) {
    def result = [] as List<Tag>
    while (true) {
      def token = tokenizer.nextToken()
      if (token == BRACK_OPEN) {
        tokenizer.nextToken()
        String key = tokenizer.sval
        if (!key) {
          break
        }
        if (tokenizer.nextToken() != QUOTE_DOUBLE) {
          tokenizer.pushBack()
          break
        }
        def value = tokenizer.sval
        if (tokenizer.nextToken() != BRACK_CLOSE) {
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

  private Integer parseStartingMoveNumber(StreamTokenizer tokenizer) {
    tokenizer.pushBack()
    
    def result = -1
    while (tokenizer.nextToken() != TT_EOF) {
      if (tokenizer.ttype == TT_NUMBER) {
        result = tokenizer.nval.intValue()
        break
      } else if (tokenizer.ttype == TT_WORD && tokenizer.sval =~ /^[1-9][0-9]*\..*$/) {
        result = tokenizer.sval.split(/\./)[0] as Integer
        break
      }
    }
    tokenizer.pushBack()
    return result > 0 ? result : 1
  }

  private String parseResult(String moveText) {
    def wonGamePattern = Pattern.compile("(1 0|0 1)\$")
    def matcher = wonGamePattern.matcher(moveText)
    if (matcher.find()) {
      def result = matcher.group(1).trim()
      result.replaceAll("\\s+", " ")
      return result.replaceAll(" ", "-")
    }

    def drawnGamePattern = Pattern.compile("(1\\s*/2-1/2)\$")
    matcher = drawnGamePattern.matcher(moveText)
    if (matcher.find()) {
      return '1/2-1/2'
    }

    return '*' // Default result if no match is found
  }

  // Modified parseMoveText to return the raw move text string
  private String parseMoveText(StreamTokenizer tokenizer) {
    int token
    // Consume tokens until the end of the stream or the start of the move text
    while ((token = tokenizer.nextToken()) != TT_EOF) {
      if (token == TT_NUMBER ||
              (token == TT_WORD && tokenizer.sval?.matches("^[1-9][0-9]*\\..*\$")) ||
              (token == TT_WORD && ["1-0", "0-1", "1/2-1/2", "*"].contains(tokenizer.sval))) {
        tokenizer.pushBack()
        break
      }
      if ('[' == tokenizer.sval) {
        tokenizer.pushBack()
        break
      }
    }

    def moveText = ''
    while ((token = tokenizer.nextToken()) != TT_EOF) {
      def word = tokenizer.sval
      if (token == PAREN_OPEN) {
        moveText += ' ( '
      } else if (token == PAREN_CLOSE) {
        moveText += ' ) '
      } else if (token == TT_NUMBER) {
        moveText += "${tokenizer.nval.intValue()} "
      } else if (token == TT_WORD) {
        moveText += "${word} "
      } else if (word =~ /^\{/) {
        def comment = ''
        comment += word.replaceAll(/\{/, '')
        while ((token = tokenizer.nextToken()) != TT_EOF && '}' != word) { //token != '}'.codePointAt(0)) {
          if (token == TT_EOL) {
            comment += ' '
          } else {
            comment += tokenizer.sval
          }
        }
        moveText += " { ${comment.trim()} } "
      }
    }
    return moveText.trim()
  }

  private Ply parseMoveTextToPlies(String moveText, StreamTokenizer tokenizer) {
    if (moveText =~ /^\s*\(.+\)\s*$/) {
      moveText = moveText.substring(1, moveText.size()-1)
    }
    List<Map<String, String>> moves = new ArrayList<>()
    def currentMove = ''
    int parenCount = 0

    for (def i = 0; i < moveText.size(); i++) {
      if ('(' == moveText[i]) {
        parenCount++
        if (! currentMove.empty) {
          moves << [move: currentMove.trim()]
          currentMove = ''
        }
        moves << [openParen: '('] 
      } else if (')' == moveText[i]) {
        parenCount--
        if (!currentMove.empty) {
          moves << [move: currentMove.trim()]
        }
        moves << [closeParen: ')']
        currentMove = ''
      } else if (moveText[i] =~ /\s+/ && parenCount == 0) {
        if (!currentMove.empty) {
          if (!(currentMove =~ /^\s*\d+\s*$/) && !(currentMove =~ /^\*$/)) {
            moves << [move: currentMove.trim()]
          }
          currentMove = ''
        }
      } else if ('{' == moveText[i]) {
        def comment = ''
        i++
        while (i < moveText.length() && moveText[i] != '}') {
          comment += moveText[i]
          i++
        }
        if (! moves.empty) {
          moves[-1]['comment'] = comment.trim()
        }
      } else if ('$' == moveText[i]) {
        def nag = ''
        i++
        while (i<moveText.size() && moveText[i].find('\\d')) {
          nag += moveText[i]
          i++
        }
        if (! moves.empty) {
          moves[-1]['nag'] = "\$${nag}" as String
        }
        i--
      }
      else {
        currentMove += moveText[i]
      }
    }
    if (!currentMove.empty) {
      if (!currentMove.find("^\\d*\$")  && !currentMove.find("^\\*\$"))
      {
        moves << [move: currentMove.trim()]
      }
    }

    Ply firstPly = null
    Ply prevPly = null
    for (def i = 0; i < moves.size(); i++) {
      def move = moves[i].move
      def comment = moves[i].comment
      def openParen = moves[i].openParen
      def closeParen = moves[i].closeParen
      def nag = moves[i].nag ?: ''
      def movePattern = Pattern.compile("\\s*([1-9][0-9]*)?\\s*")
      if (!move || move.empty) {
        if (openParen) {
          if (prevPly) {
            def variationText = '( '
            int variationParenCount = 1
            i++
            while (i < moves.size() && variationParenCount > 0) {
              if(moves[i].openParen) {
                variationParenCount++
                variationText += ' ( '
              } else if (moves[i].closeParen) {
                variationParenCount--
                variationText += ' ) '
              } else {
                variationText += "${moves[i].move} "
              }
              i++
            }
            def variationPly = parseMoveTextToPlies(variationText.trim(), tokenizer)
            if (comment) {
              variationPly.commentAfter = comment
            }
            prevPly.variations << variationPly
            i--
            continue
          }
        }
        continue // Skip empty strings
      } else if (move.matches("\\s*[1-9][0-9]*\\.\\s*")) {
        continue
      }

      def currentPly = new Ply(san: move, prev: prevPly, commentAfter: comment, nag: nag)
      if (!firstPly) {
        firstPly = currentPly
      }
      if (prevPly) {
        prevPly.next = currentPly
      }
      prevPly = currentPly
    }
    return firstPly
  }
}
