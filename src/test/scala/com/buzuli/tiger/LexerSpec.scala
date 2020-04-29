package com.buzuli.tiger

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Graph}
import akka.stream.scaladsl.Source

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Try}

class OrgAuthSpec

class LexerSpec extends AnyWordSpec with Matchers {
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

    // String
    "lexing a string" should {
      "handle a simple string" in {
        validate("\"howdy\"") {
          TokenString("howdy") :: Nil
        }
      }

      "accept valid escape sequences" in {
        validate("\"\\n\"") { TokenString("\n") :: Nil }
        validate("\"\\^c\"") { TokenString(3.toChar.toString) :: Nil }
        validate("\"\\123\"") { TokenString("{") :: Nil }
        validate("\"\\\"\"") { TokenString("\"") :: Nil }
        validate("\"\\\\\"") { TokenString("\\") :: Nil }
        validate("\"\\   \\\"") { TokenString("") :: Nil }
        validate("\"a\\   \\b\"") { TokenString("ab") :: Nil }
      }

      "reject on incomplete input" in {
        invalidate("\"howdy")
      }

      "reject invalid escape sequences" in {
        invalidate("\"\\q\"")
        invalidate("\"\\_\"")
        invalidate("\"\\23\"")
      }
    }

    // Integer
    "lexing an integer" should {
      "should consume a standalone value" in {
        validate("1") { TokenInteger(1) :: Nil }
        validate("13") { TokenInteger(13) :: Nil }
      }

      "should strip surrounding whitespace" in {
        validate(" 1 ") { TokenInteger(1) :: Nil }
        validate("\n12\n") { TokenInteger(12) :: Nil }
      }
    }

    // Multi-token inputs
    "lexing structures" should {
      "handle json strings" in {
        validate("{\"howdy\":\"there\"}") {
          TokenBraceOpen :: TokenString("howdy") :: TokenColon :: TokenString("there") :: TokenBraceClose :: Nil
        }
      }

      "ignore whitespace" in {
        validate("{ \"k\" : \"v\" }") {
          TokenBraceOpen :: TokenString("k") :: TokenColon :: TokenString("v") :: TokenBraceClose :: Nil
        }
      }
    }
  }
}
