package com.bahmanm.pgn

import groovy.transform.Canonical

import com.bahmanm.pgn.models.*
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.util.regex.Matcher
import java.util.regex.Pattern

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
    while (tokenizer.nextToken() != StreamTokenizer.TT_EOF) {
      if (tokenizer.ttype == StreamTokenizer.TT_NUMBER) {
        firstMoveNumber = (int) tokenizer.nval;
        break;
      }
      if (tokenizer.ttype == StreamTokenizer.TT_WORD && tokenizer.sval?.matches("^[1-9][0-9]*\\..*\$")) {
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
    StringBuilder moveText = new StringBuilder();
    int token;

    // Consume tokens until the end of the stream or the start of the move text
    while ((token = tokenizer.nextToken()) != StreamTokenizer.TT_EOF) {
      if (token == StreamTokenizer.TT_NUMBER ||
              (token == StreamTokenizer.TT_WORD && tokenizer.sval?.matches("^[1-9][0-9]*\\..*\$")) ||
              (token == StreamTokenizer.TT_WORD && ["1-0", "0-1", "1/2-1/2", "*"].contains(tokenizer.sval))) {
        tokenizer.pushBack();
        break;
      }
      if (token == '[') {
        tokenizer.pushBack();
        break;
      }
    }

    while ((token = tokenizer.nextToken()) != StreamTokenizer.TT_EOF) {
      String word = tokenizer.sval;
      if (token == '(') {
        moveText.append(" ( ");
      } else if (token == ')') {
        moveText.append(" ) ");
      }
      else if (token == StreamTokenizer.TT_NUMBER) {
        moveText.append((int) tokenizer.nval).append(" ");
      }
      else if (token == StreamTokenizer.TT_WORD){
        moveText.append(word).append(" ");
      } else if (word.find('^{')) {
        StringBuilder comment = new StringBuilder();
        comment.append(word.replaceAll('\\{', ''))
        while ((token = tokenizer.nextToken()) != StreamTokenizer.TT_EOF && token != '}'.codePointAt(0)) {
          if (token == StreamTokenizer.TT_EOL) {
            comment.append(" ");
          } else {
            comment.append(tokenizer.sval);
          }
        }
        moveText.append(" { ").append(comment.toString().trim()).append(" } ");
      }
    }
    return moveText.toString().trim();
  }

  private Ply parseMoveTextToPlies(String moveText, StreamTokenizer tokenizer) {
    if (moveText.find('^\\s*\\(.+\\)\\s*$')) {
      moveText = moveText.substring(1, moveText.size()-1)
    }
    Ply firstPly = null;
    Ply prevPly = null;
    List<Map<String, String>> moves = new ArrayList<>();
    StringBuilder currentMove = new StringBuilder();
    int parenCount = 0;
    boolean expectMoveText = true;
    String currentComment = null;

    for (int i = 0; i < moveText.length(); i++) {
      char c = moveText.charAt(i);
      if (c == '('.codePointAt(0)) {
        parenCount++;
        if (currentMove.length() > 0) {
          moves << [move: currentMove.toString().trim()]
          currentMove.setLength(0);
        }
        moves << [openParen: "("] // Add this line
        expectMoveText = true;
      } else if (c == ')'.codePointAt(0)) {
        parenCount--;
        if (currentMove.length() > 0) {
          moves << [move: currentMove.toString().trim()]
          currentMove.setLength(0);
        }
        moves << [closeParen: ")"]  //and this line
        expectMoveText = true;
      }
      else if (Character.isWhitespace(c) && parenCount == 0) {
        if (currentMove.length() > 0) {
          if (!currentMove.find("^\\d*\$")  && !currentMove.find("^\\*\$"))
          {
            moves << [move: currentMove.toString().trim()]
          }
          currentMove.setLength(0);
        }
        expectMoveText = true;
      } else if (c == '{'.codePointAt(0)) {
        StringBuilder comment = new StringBuilder();
        i++;
        while (i < moveText.length() && moveText[i] != '}') {
          comment.append(moveText.charAt(i));
          i++;
        }
        currentComment = comment.toString().trim();
        if(moves.size()>0)
          moves[-1]['comment'] = currentComment
        expectMoveText = false;
      }
      else {
        currentMove.append(c);
        expectMoveText = false;
      }
    }
    if (currentMove.length() > 0) {
      if (!currentMove.find("^\\d*\$")  && !currentMove.find("^\\*\$"))
      {
        moves << [move: currentMove.toString().trim()]
      }
    }

    for (int i = 0; i < moves.size(); i++) {
      def move = moves[i].move
      def comment = moves[i].comment
      def openParen = moves[i].openParen
      def closeParen = moves[i].closeParen
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
            prevPly.variations << variationPly;
            i--;
            continue;
          }
        }
        continue; // Skip empty strings
      } else if (move.matches("\\s*[1-9][0-9]*\\.\\s*")) {
        continue;
      }


      Ply currentPly = new Ply(san: move, prev: prevPly, commentAfter: comment);
      currentComment = null;
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
