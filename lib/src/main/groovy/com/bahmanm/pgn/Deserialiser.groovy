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

  /**
   * Deserializes a PGN string from a  into a Game object.
   *
   * @param pgn The PGN data as a String.
   * @return A Game object representing the parsed PGN data, or null if parsing fails.
   * @throws IllegalArgumentException if the input String is null
   */
  Game deserialise(String pgn) {
    if (pgn == null) {
      throw new IllegalArgumentException("PGN string cannot be null")
    }

    try {
      StringReader reader = new StringReader(pgn);
      StreamTokenizer tokenizer = new StreamTokenizer(reader);
      tokenizer.resetSyntax();
      tokenizer.wordChars('!'.codePointAt(0), '~'.codePointAt(0));
      tokenizer.whitespaceChars('\n'.codePointAt(0), ' '.codePointAt(0));
      tokenizer.quoteChar('"'.codePointAt(0));
      //tokenizer.commentChar('{'.codePointAt(0));
      tokenizer.ordinaryChar('['.codePointAt(0));
      tokenizer.ordinaryChar(']'.codePointAt(0));
      tokenizer.ordinaryChar('('.codePointAt(0));
      tokenizer.ordinaryChar(')'.codePointAt(0));
      tokenizer.parseNumbers();  // Keep number parsing for tags and move number
      tokenizer.eolIsSignificant(false);

      List<Tag> tags = parseTags(tokenizer);
      Integer startingMoveNumber = parseStartingMoveNumber(tags, tokenizer);
      String moveText = parseMoveText(tokenizer); // Get move text as a string
      String result = parseResult(moveText);
      Ply firstPly = parseMoveTextToPlies(moveText, tokenizer); // *Process the move text string*
      return new Game(tags, startingMoveNumber, firstPly, result);
    } catch (IOException e) {
      log.error("Error during deserialization: ${e.message}", e);
      throw new RuntimeException(e);
    } finally {
      // No need to close StringReader.
    }
  }

  private Integer parseStartingMoveNumber(List<Tag> tags, StreamTokenizer tokenizer) {
    // Default starting move number
    int startingMoveNumber = 1;
    int firstMoveNumber = -1;

    //save the tokenizer state
    tokenizer.pushBack();

    // Iterate through the tokens until we find a move number
    while (tokenizer.nextToken() != TT_EOF) {
      if (tokenizer.ttype == TT_NUMBER) {
        firstMoveNumber = (int) tokenizer.nval;
        break;
      }
      if (tokenizer.ttype == TT_WORD && tokenizer.sval?.matches("^[1-9][0-9]*\\..*\$")) {
        String number = tokenizer.sval.split("\\.")[0];
        firstMoveNumber = Integer.parseInt(number);
        break;
      }
    }
    if (firstMoveNumber > 0) {
      startingMoveNumber = firstMoveNumber;
    }

    tokenizer.pushBack();
    return startingMoveNumber;
  }

  private List<Tag> parseTags(StreamTokenizer tokenizer) {
    List<Tag> tags = [];
    while (true) {
      int token = tokenizer.nextToken();
      if (token == '[') {
        tokenizer.nextToken();
        String key = tokenizer.sval;
        if (key == null) {
          break;
        }
        if (tokenizer.nextToken() != '"'.codePointAt(0)) {
          tokenizer.pushBack();
          break;
        }
        String value = tokenizer.sval;
        if (tokenizer.nextToken() != ']') {
          tokenizer.pushBack();
          break;
        }
        tags << new Tag(key, value);
      } else {
        tokenizer.pushBack();
        break;
      }
    }
    return tags;
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

    return '*'; // Default result if no match is found
  }

  // Modified parseMoveText to return the raw move text string
  private String parseMoveText(StreamTokenizer tokenizer) {
    def moveText = ''
    int token;

    // Consume tokens until the end of the stream or the start of the move text
    while ((token = tokenizer.nextToken()) != TT_EOF) {
      if (token == TT_NUMBER ||
              (token == TT_WORD && tokenizer.sval?.matches("^[1-9][0-9]*\\..*\$")) ||
              (token == TT_WORD && ["1-0", "0-1", "1/2-1/2", "*"].contains(tokenizer.sval))) {
        tokenizer.pushBack();
        break;
      }
      if ('[' == tokenizer.sval) {
        tokenizer.pushBack();
        break;
      }
    }

    while ((token = tokenizer.nextToken()) != TT_EOF) {
      def word = tokenizer.sval;
      if ('(' == word) {
        moveText += ' ( '
      } else if (')' == word) {
        moveText += ' ) '
      } else if (token == TT_NUMBER) {
        moveText += "${tokenizer.nval.intValue()} "
      } else if (token == TT_WORD){
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
      moveText = moveText.substring(1, -1)
    }
    Ply firstPly = null;
    Ply prevPly = null;
    List<Map<String, String>> moves = new ArrayList<>();
    def currentMove = ''
    int parenCount = 0;

    for (int i = 0; i < moveText.length(); i++) {
      if ('(' == moveText[i]) {
        parenCount++;
        if (! currentMove.empty) {
          moves << [move: currentMove.trim()]
          currentMove = ''
        }
        moves << [openParen: "("] // Add this line
      } else if (')' == moveText[i]) {
        parenCount--;
        if (!currentMove.empty) {
          moves << [move: currentMove.trim()]
        }
        moves << [closeParen: ")"]  //and this line
      } else if (moveText[i] =~ /\s+/ && parenCount == 0) {
        if (!currentMove.empty) {
          if (!(currentMove =~ /^\s*\d+\s*$/) && !(currentMove =~ /^\*$/)) {
            moves << [move: currentMove.trim()]
          }
          currentMove = ''
        }
      } else if ('{' == moveText[i]) {
        def comment = ''
        i++;
        while (i < moveText.length() && moveText[i] != '}') {
          comment += moveText[i]
          i++;
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

    for (int i = 0; i < moves.size(); i++) {
      def move = moves[i].move
      def comment = moves[i].comment
      def openParen = moves[i].openParen
      def closeParen = moves[i].closeParen
      def nag = moves[i].nag
      def movePattern = Pattern.compile("\\s*([1-9][0-9]*)?\\s*")
      if (!move || move.empty) {
        if(openParen != null){
          if (prevPly != null)
          {
            StringBuilder variationText = new StringBuilder().append('( ');
            int variationParenCount = 1;
            i++;
            while (i < moves.size() && variationParenCount > 0) {
              if(moves[i].openParen != null){
                variationParenCount++;
                variationText.append(" ( ")
              }
              else if (moves[i].closeParen != null){
                variationParenCount--
                variationText.append(" ) ")
              }
              else{
                variationText.append(moves[i].move).append(" ");
              }
              i++;
            }
            Ply variationPly = parseMoveTextToPlies(variationText.toString().trim(), tokenizer);
            if (comment) {
              variationPly.commentAfter = comment
            }
            prevPly.variations << variationPly;
            i--;
            continue;
          }
        }
        continue; // Skip empty strings
      } else if (move.matches("\\s*[1-9][0-9]*\\.\\s*")) {
        continue;
      }

      def currentPly
      if (nag) {
        currentPly = new Ply(san: move, prev: prevPly, commentAfter: comment, nag: nag)        
      } else {
        currentPly = new Ply(san: move, prev: prevPly, commentAfter: comment)
      }
      if (firstPly == null) {
        firstPly = currentPly;
      }
      if (prevPly != null) {
        prevPly.next = currentPly;
      }
      prevPly = currentPly;
    }
    return firstPly;
  }
}
