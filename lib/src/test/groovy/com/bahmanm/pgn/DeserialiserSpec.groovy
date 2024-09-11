package com.bahmanm.pgn

import spock.lang.Specification

class DeserialiserSpec extends Specification {
  def "ok"() {
    setup:
    def deserialiser = new Deserialiser()

    when:
    def result = deserialiser.apply('foo/bar.pgn')

    then:
    result
  }
}
