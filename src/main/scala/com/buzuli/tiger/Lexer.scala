package com.buzuli.tiger

import java.io.InputStream

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}
import akka.stream.{Attributes, FlowShape, Graph}
import akka.util.ByteString

object Lexer {
  def create: Graph[FlowShape[Char, Token], NotUsed] = {
    var state: Option[TokenContext] = None
    Flow[Char]
      .map[Option[Token]] { c =>

      }
  }
}

case class LexemeChar()

case class Lexeme(value: String)

class LexerContext {

}

object LexerFsm {

}

sealed trait LexerState {
}

object LexerStart extends LexerState
object LexerEnd extends LexerState
