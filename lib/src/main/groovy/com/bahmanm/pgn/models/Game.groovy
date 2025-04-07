package com.bahmanm.pgn.models

import groovy.transform.Canonical

@Canonical
class Game {
  
  enum Side {
    WHITE,
    BLACK
  }

  List<Tag> tags
  Integer startingMoveNumber
  Side startingSide
  Ply firstPly
  String result
}
