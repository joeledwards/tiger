package com.buzuli.tiger

import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Try}

class ParserSpec extends AnyWordSpec with Matchers {
  implicit val ec = ExecutionContext.Implicits.global
  implicit val system = ActorSystem("tiger-spec")
  import system.dispatcher
  implicit val materializer = ActorMaterializer

  val MAX_WAIT = Duration(5, TimeUnit.SECONDS)

  def source(text: String): Source[Char, NotUsed] = Source.fromIterator(() => text.iterator)
  def collect[Token](source: Source[Token, NotUsed]): Future[List[Token]] = {
    source.runFold[List[Token]](Nil) { (tokens, token) => tokens :+ token }
  }

  def validate(input: String)(expectedOutput: => List[Token]): Any = {
    val output = Await.result(collect(source(input).via(Lexer.create)), MAX_WAIT)
    assert(output == expectedOutput)
  }

  def invalidate(input: String): Any = {
    Try {
      Await.result(collect(source(input).via(Lexer.create)), MAX_WAIT)
    } match {
      case Failure(_: LexerError) =>
      case _ => assert(false)
    }
  }

  "Lexer" when {
    // Testing Scala behaviors
    "StringBuilder" should {
      "initialize with a character" in {
        var sb = new StringBuilder
        sb += 'a'
        assert(sb.toString == "a")
        //assert(new StringBuilder('a').toString == "a")
      }
    }
  }
}
