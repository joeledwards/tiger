package com.buzuli.tiger

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}

sealed trait ConsumeResult[IN, OUT]
case class ConsumeContinue[IN, OUT]() extends ConsumeResult[IN, OUT]
case class ConsumeContinueWithNewConsumer[IN, OUT](newConsumer: Consumer[IN, OUT]) extends ConsumeResult[IN, OUT]
case class ConsumeComplete[IN, OUT](result: Option[OUT], extra: Option[IN] = None) extends ConsumeResult[IN, OUT]
case class ConsumeReject[IN, OUT](error: TigerError) extends ConsumeResult[IN, OUT]

trait Consumer[IN, OUT] {
  def consume(value: IN): ConsumeResult[IN, OUT]
  def end(): ConsumeResult[IN, OUT]
}

abstract class InterpreterStage[IN, OUT] {
  def init: Unit
  def consumer: Consumer[IN, OUT]

  def create: Flow[Char, Token, NotUsed] = {
    // Notifies us when the input stream has completed
    val endIndicator: List[Option[Char]] = None :: Nil
    val inputEnd: Source[Option[Char], NotUsed] = Source.fromIterator(() => endIndicator.iterator)

    Flow[Char]
      .map[Option[Char]](Some(_))
      .concat[Option[Char], NotUsed](inputEnd)
      .mapConcat[Token]((char: Option[Char]) => {
        val tokens: List[Token] = (char, context) match {
          case (Some(c), _) => consumeChar(c)
          case (None, Some(consumer)) => {
            // We reached the end of input, but we still have active context
            consumer.end() match {
              case ConsumeComplete(Some(token), _) => token :: Nil
              case ConsumeComplete(None, _) => Nil
              case ConsumeReject(error) => throw error
              case _ => {
                throw LexerError(s"Unexpected end of stream with active consumer ${consumer}", line, column)
              }
            }
          }
          case _ => Nil
        }

        char match {
          case Some('\n') => {
            line += 1
            column = 0
          }
          case Some(_) => {
            column += 1
          }
          case None =>
        }

        tokens
      })
  }
}
