package com.bahmanm.pgn.models

import groovy.transform.Canonical

@Canonical
class Ply {

  String san
  Ply next = null
  Ply prev = null
  List<Ply> variations = []

  String commentBefore = ''
  String commentAfter = ''
  String nag = ''
  
  @Override
  String toString() {
    "${commentBefore} ${san} ${commentAfter}"
  }
}
