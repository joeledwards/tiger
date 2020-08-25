package com.buzuli.tiger

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.stream.{FlowShape, Graph}

case class IrNode(token: Token) {
}

sealed trait ParseConsumeResult
case object ParseConsumeContinue extends ParseConsumeResult
case class ParseConsumeContinueWithNewConsumer(newConsumer: ParseConsumer) extends ParseConsumeResult
case class ParseConsumeComplete(token: Option[Token], char: Option[Char] = None) extends ParseConsumeResult
case class ParseConsumeReject(error: TigerError) extends ParseConsumeResult

trait ParseConsumer {
  def nom(token: Token): ParseConsumeResult
  def end(): ParseConsumeResult = ParseConsumeReject(ParserError("Unexpected end of context"))
}

object Parser {
  def create: Flow[Token, IrNode, NotUsed] = {
    // TODO: build it

    Flow[Token].map(IrNode(_))
  }
}
