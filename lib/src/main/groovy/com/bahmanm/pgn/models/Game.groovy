package com.bahmanm.pgn.models

import groovy.transform.Canonical

@Canonical
class Game {

  List<Tag> tags
  Integer startingMoveNumber
  Ply firstPly
}
