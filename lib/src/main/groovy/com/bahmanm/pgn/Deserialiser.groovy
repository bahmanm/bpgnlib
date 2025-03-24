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
      tokenizer.commentChar('{'.codePointAt(0));
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
      Ply firstPly = parseMoveTextToPlies(moveText); // *Process the move text string*
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
      }
    }
    return moveText.toString().trim();
  }

  private Ply parseMoveTextToPlies(String moveText) {
    Ply firstPly = null;
    Ply prevPly = null;
    List<String> moves = new ArrayList<>();
    StringBuilder currentMove = new StringBuilder();
    int parenCount = 0;
    boolean expectMoveText = true;

    for (int i = 0; i < moveText.length(); i++) {
      char c = moveText.charAt(i);
      if (c == '(') {
        parenCount++;
        if (currentMove.length() > 0) {
          moves.add(currentMove.toString().trim());
          currentMove.setLength(0);
        }
        currentMove.append(c);
        expectMoveText = true;
      } else if (c == ')') {
        parenCount--;
        if (currentMove.length() > 0) {
          moves.add(currentMove.toString().trim());
          currentMove.setLength(0);
        }
        currentMove.append(c);
        expectMoveText = true;
      }
      else if (Character.isWhitespace(c) && parenCount == 0) {
        if (currentMove.length() > 0) {
          if (!currentMove.find("^\\d*\$") && !currentMove.find("^\\*\$")) {
            moves.add(currentMove.toString().trim());
          }
          currentMove.setLength(0);
        }
        expectMoveText = true;
      } else {
        currentMove.append(c);
        expectMoveText = false;
      }
    }
    if (currentMove.length() > 0) {
      if (!currentMove.find("^\\d*\$") && !currentMove.find("^\\*\$")) {
        moves.add(currentMove.toString().trim());
      }
    }
    String[] movesArray = moves.toArray(new String[0]);


    for (int i = 0; i < movesArray.length; i++) {
      String move = movesArray[i];
      if (move.isEmpty()) {
        continue; // Skip empty strings
      }
      if (move.matches("\\s*[1-9][0-9]*\\.\\s*"))
      {
        continue;
      }
      if (move.equals("(")) {
        StringBuilder variationText = new StringBuilder();
        int variationParenCount = 1;
        i++;
        while (i < movesArray.length && variationParenCount > 0) {
          String currentVarMove = movesArray[i];
          if (currentVarMove.equals("(")) {
            variationParenCount++;
          } else if (currentVarMove.equals(")")) {
            variationParenCount--;
          }
          if (variationParenCount > 0) {
            variationText.append(currentVarMove).append(" ");
          }
          i++;
        }
        Ply variationPly = parseMoveTextToPlies(variationText.toString().trim());
        prevPly.variations << variationPly;
        i--;
        continue;
      }
      if (move.equals(")")) {
        break;
      }

      Ply currentPly = new Ply(san: move, prev: prevPly);
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
