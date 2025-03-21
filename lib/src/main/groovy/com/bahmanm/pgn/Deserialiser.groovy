package com.bahmanm.pgn

import groovy.transform.Canonical

import com.bahmanm.pgn.models.*
import groovy.util.logging.Slf4j

@Slf4j
class Deserialiser {

  /**
   * Deserializes a PGN string into a Game object.
   *
   * @param text The PGN string to deserialize.
   * @return A Game object representing the parsed PGN data, or null if parsing fails.
   * @throws IllegalArgumentException if the input text is null or empty
   */
  Game deserialise(String text) {
    if (!text) {
      throw new IllegalArgumentException("PGN text cannot be null or empty")
    }

    StringReader reader = new StringReader(text)
    StreamTokenizer tokenizer = new StreamTokenizer(reader)
    tokenizer.resetSyntax()
    tokenizer.wordChars('!'.codePointAt(0), '~'.codePointAt(0)) // Define word characters
    tokenizer.whitespaceChars('\n'.codePointAt(0), ' '.codePointAt(0)) // Define whitespace characters.  Important to include \n.
    tokenizer.quoteChar('"'.codePointAt(0)) // Define quote character
    tokenizer.commentChar('{'.codePointAt(0)) // Define comment character.
    tokenizer.ordinaryChar('['.codePointAt(0)) //needed for tags
    tokenizer.ordinaryChar(']'.codePointAt(0)) //needed for tags
    tokenizer.ordinaryChar('('.codePointAt(0)) //needed for variations
    tokenizer.ordinaryChar(')'.codePointAt(0)) //needed for variations
    tokenizer.parseNumbers() // Enable number parsing
    tokenizer.eolIsSignificant(false)
    
    List<Tag> tags = parseTags(tokenizer)
    Integer startingMoveNumber = parseStartingMoveNumber(tags) // Parse from tags, default 1
    Ply firstPly = parseMoveText(tokenizer)
    String result = parseResult(tokenizer)

    return new Game(tags, startingMoveNumber, firstPly, result)
  }

  private Integer parseStartingMoveNumber(List<Tag> tags) {
    Tag startNumTag = tags.find { it.key.equalsIgnoreCase("StartPly") }
    if (startNumTag?.value?.isInteger()) {
      return startNumTag.value.toInteger()
    }
    return 1 // Default
  }

  private List<Tag> parseTags(StreamTokenizer tokenizer) {
    List<Tag> tags = []
    while (true) {
      int token = tokenizer.nextToken();
      if (token == '[') {
        tokenizer.nextToken(); // Consume the tag name
        String key = tokenizer.sval;
        if (key == null) {
          break // Error or end of tags
        }
        if (tokenizer.nextToken() != '"'.codePointAt(0)) {
          tokenizer.pushBack()
          break;
        }
        String value = tokenizer.sval
        if (tokenizer.nextToken() != ']'.codePointAt(0)) {
          tokenizer.pushBack()
          break // Error: expected closing bracket
        }
        tags << new Tag(key, value)
      } else {
        tokenizer.pushBack();
        break;
      }
    }
    return tags
  }

  private String parseResult(StreamTokenizer tokenizer) {
    String result = "*" // Default
    while (tokenizer.nextToken() != StreamTokenizer.TT_EOF) {
      if (tokenizer.ttype == StreamTokenizer.TT_WORD) {
        if (tokenizer.sval in ["1-0", "0-1", "1/2-1/2", "*"]) {
          result = tokenizer.sval
          break
        }
      }
    }
    return result
  }

  private Ply parseMoveText(StreamTokenizer tokenizer) {
    Ply firstPly = null
    Ply prevPly = null
    Ply currentPly = null; //added
    String savedWord = null;
    boolean moveTextStarted = false;

    // Consume tokens until the end of the stream or the start of the move text
    while (tokenizer.nextToken() != StreamTokenizer.TT_EOF) {
      if (tokenizer.ttype == StreamTokenizer.TT_NUMBER ||
              (tokenizer.ttype == StreamTokenizer.TT_WORD && tokenizer.sval?.matches("^[1-9][0-9]*\\..*\$"))) {
        tokenizer.pushBack();
        moveTextStarted = true;
        break; // Start of move text
      }
      if(tokenizer.ttype == '[') {
        tokenizer.pushBack();
        break;
      }
    }

    while (tokenizer.nextToken() != StreamTokenizer.TT_EOF) {
      if (tokenizer.ttype == '(') {
        //parse variation
        if (prevPly != null) { // variations *must* be after a move.
          Ply variationPly = parseMoveText(tokenizer) // Recursive call for variations
          prevPly.variations << variationPly
        }
        while (tokenizer.nextToken() != ')' && tokenizer.ttype != StreamTokenizer.TT_EOF) {
          tokenizer.pushBack()
          parseMoveText(tokenizer)
        }
        if (tokenizer.ttype == StreamTokenizer.TT_EOF) {
          break //error
        }
        continue
      } else if (tokenizer.ttype == ')') {
        break // End of variation
      }

      if (tokenizer.ttype == StreamTokenizer.TT_NUMBER) {
        // Skip move number
        tokenizer.nextToken() // Consume the following dot (or ellipsis)
        if (tokenizer.ttype == '.') {
          //do nothing
        } else if (tokenizer.ttype == StreamTokenizer.TT_NUMBER) {
          tokenizer.nextToken()
        }
      }

      if (tokenizer.ttype == StreamTokenizer.TT_WORD) {
        String word = tokenizer.sval
        if (savedWord != null) {
          word = savedWord
          savedWord = null
        }

        if (word in ["1-0", "0-1", "1/2-1/2", "*"]) {
          tokenizer.pushBack()
          break // End of move text
        }

        if (word.startsWith('{')) {
          if (prevPly != null) {
            prevPly.commentBefore = word.substring(1, word.length() - 1)
          }
          continue
        }

        if (word.startsWith('}')) {
          if (prevPly != null) {
            prevPly.commentAfter = word.substring(1, word.length() - 1)
          }
          continue
        }

        // Try to parse NAG (Numeric Annotation Glyph)
        if (word.startsWith('$')) {
          try {
            int nagValue = Integer.parseInt(word.substring(1))
            if (prevPly != null) {
              prevPly.nag = word // Store the whole NAG string
            }
            continue
          } catch (NumberFormatException e) {
            // Not a valid NAG, treat as a move
          }
        }

        if (currentPly == null) {
          currentPly = new Ply(san: word, prev: prevPly)
          firstPly = currentPly
        } else {
          currentPly = new Ply(san: word, prev: prevPly)
          prevPly.next = currentPly
        }

        prevPly = currentPly
      }
    }
    return firstPly
  }
}
