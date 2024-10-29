package com.bahmanm.pgn

import spock.lang.Specification

import com.bahmanm.pgn.models.*

class DeserialiserSpec extends Specification {

  def 'should return null when passed an empty text'() {
    setup:
    def deserialiser = new Deserialiser()

    when:
    def result = deserialiser.apply('')

    then:
    result == null
  }

  def 'should be able to deserialise a perfectly formed PGN file'() {
    setup:
    def deserialiser = new Deserialiser()
    def text = deserialiser.class.getResource('/perfectly-formed.pgn')

    when:
    def result = deserialiser.apply(text)

    then:
    result
  }

  def 'should be able to deserialise a minimal PGN'() {
    setup:
    def deserialiser = new Deserialiser()
    def text = '''
[White "A"]
[Black "B"]
[Result "1/2-1/2"]

1. d4 d5 2. c4 e6 3. Nc3 Nf6 4. Bg5 Be7 5. Nf3 1/2-1/2
'''
    when:
    def result = deserialiser.apply(text)

    then:
    result.tags.size() == 3
    new Tag('White', '"A"') in result.tags
    new Tag('Black', '"B"') in result.tags
    new Tag('Result', '"1/2-1/2"') in result.tags
    result.result == "1/2-1/2"
    result.startingMoveNumber == 1
    result.firstPly
  }
}
