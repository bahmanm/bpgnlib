package com.bahmanm.pgn


import com.bahmanm.pgn.models.*
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

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
      def moveText = getSanitisedMovesText(tokenizer) // Get move text as a string
      def result = parseResult(moveText)
      def firstPly = parseMoveTextToPlies(moveText) // *Process the move text string*
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
    if (moveText =~ /.*(1\s*0|0\s*1)$/) {
      return moveText.find(/(1\s*0|0\s*1)$/)
              .trim()
              .replaceAll(/\s+/, ' ')
              .replaceAll(' ', '-')
    } else if (moveText =~ /.*1\s*\/2-1\/2$/) {
      return '1/2-1/2'
    } else {
      return '*'
    }
  }
  
  private void skipTokensUntilFirstMove(StreamTokenizer tokenizer) {
    def token
    def found = false

    while (!found && (token = tokenizer.nextToken()) != TT_EOF) {
      if (token == TT_NUMBER) {
        found = true        
      } else if (token == TT_WORD) {
        if (tokenizer.sval =~ /^[1-9][0-9]*\..*$/) {
          found = true          
        } else if (tokenizer.sval in ['1-0', '0-1', '1/2-1/2', '*']) {
          found = true
        }
      } else if (token == BRACK_OPEN) {
        found = true
      }
    }    
    if (found) {
      tokenizer.pushBack()
    }
  }
  
  private String parseComment(StreamTokenizer tokenizer, String text) {
    def result = ''
    result += text.replaceAll(/\{/, '')
    
    def token
    while ((token = tokenizer.nextToken()) != TT_EOF && '}' != tokenizer.sval) {
      if (token == TT_EOL) {
        result += ' '
      } else {
        result += tokenizer.sval
      }
    }    
    return result.trim()
  }
  
  private String getSanitisedMovesText(StreamTokenizer tokenizer) {
    skipTokensUntilFirstMove(tokenizer)
    
    def token
    def result = ''
    while ((token = tokenizer.nextToken()) != TT_EOF) {
      if (token == PAREN_OPEN) {
        result += ' ( '
      } else if (token == PAREN_CLOSE) {
        result += ' ) '
      } else if (token == TT_NUMBER) {
        result += "${tokenizer.nval.intValue()} "
      } else if (token == TT_WORD) {
        result += "${tokenizer.sval} "
      } else if (tokenizer.sval =~ /^\{/) {
        result += " { ${parseComment(tokenizer, tokenizer.sval)} } "
      }
    }
    return result.trim()
  }
  
  private List<Map<String, String>> getElements(String movesText) {
    if (movesText =~ /^\s*\(.+\)\s*$/) {
      movesText = movesText.substring(1, movesText.size()-1)
    }
    
    def result = [] as List<Map<String, String>>
    def parenCount = 0
    def currentMove = ''
    def inComment = false
    def currentComment = ''
    def inNag = false
    def currentNag = ''

    for (def s : movesText) {
      if ('(' == s) {
        parenCount += 1
        if (! currentMove.empty) {
          result << [move: currentMove.trim()]
          currentMove = ''
        }
        result << [openParen: '(']
      } else if (')' == s) {
        parenCount -= 1
        if (!currentMove.empty) {
          result << [move: currentMove.trim()]
        }
        result << [closeParen: ')']
        currentMove = ''
      } else if ('{' == s) {
        inComment = true
      } else if (inComment) {
        if ('}' == s) {
          if (!result.empty) {
            result[-1]['comment'] = currentComment.trim()
          }
          inComment = false          
          currentComment = ''
        } else {
          currentComment += s
        }
      } else if ('$' == s) {
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
      } else if (s =~ /\s+/ && parenCount == 0) {
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

  private Ply parseMoveTextToPlies(String moveText) {
    def moves = getElements(moveText)
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
            def variationPly = parseMoveTextToPlies(variationText.trim())
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
