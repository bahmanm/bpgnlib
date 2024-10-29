package com.bahmanm.pgn

import groovy.transform.Canonical

import com.bahmanm.pgn.models.*

class Deserialiser {

  @Canonical
  class Context {

    Integer index = 0
    String text
  }

  Game apply(String text) {
    if (text.empty) {
      return null
    }

    def ctx = new Context(text: text, index: 0)
    def tags = getTags(ctx)
    return new Game(tags: tags)
  }

  List<Tag> getTags(ctx) {
    def result = []
    def tag
    while ((tag = getTag(ctx)))
      result << tag
    return result
  }

  Tag getTag(ctx) {
    def openBracketIndex = ctx.text.indexOf('[', ctx.index)
    if (openBracketIndex == -1)
      return null
    def closeBracketIndex = ctx.text.indexOf(']', openBracketIndex)
    if (closeBracketIndex == -1)
      return null
    def firstWhitespaceIndex = ctx.text.indexOf(' ', openBracketIndex)
    if (firstWhitespaceIndex == -1) {
      ctx.index = closeBracketIndex + 1
      return new Tag(key: ctx.text[openBracketIndex+1..closeBracketIndex-1], value: '""')
    } else {
      ctx.index = closeBracketIndex + 1
      return new Tag(key: ctx.text[openBracketIndex+1..firstWhitespaceIndex-1],
                     value: ctx.text[firstWhitespaceIndex+1..closeBracketIndex-1])
    }
  }
}
