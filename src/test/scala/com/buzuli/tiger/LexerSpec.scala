package com.buzuli.tiger

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Graph}
import akka.stream.scaladsl.Source
import com.buzuli.tiger

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

    // Comments
    "lexing a comment" should {
      "discard a stand-alone, single-line comment" in {
        validate("""/* this is a single-line comment */""") { Nil }
      }

      "discard a stand-alone, multi-line comment" in {
        validate(
          """
            |/*
            | this is a multi-
            | line comment
            |*/""".stripMargin
        ) { Nil }
      }

      "discard a single-line comment, keeping code" in {
        validate(
          """
            |/* a single value */
            |identifier := "value"
            |""".stripMargin
        ) {
          TokenId("identifier") ::
          TokenAssignment ::
          TokenString("value") ::
          Nil
        }

        validate(
          """
            |id := "value" /* there is another value after this one */
            |di := "eulav"
            |""".stripMargin
        ) {
          TokenId("id") ::
          TokenAssignment ::
          TokenString("value") ::
          TokenId("di") ::
          TokenAssignment ::
          TokenString("eulav") ::
          Nil
        }
      }

      "discard a multi-line comment, keeping code" in {
        validate(
          """
            |/*
            | there is only
            | one value
            |*/
            |a := 1
            |""".stripMargin
        ) {
          TokenId("a") ::
          TokenAssignment ::
          TokenInteger(1) ::
          Nil
        }

        validate(
          """
            |a := 1 /*
            | there is only
            | one value
            |*/
            |b := 2
            |""".stripMargin
        ) {
          TokenId("a") ::
          TokenAssignment ::
          TokenInteger(1) ::
          TokenId("b") ::
          TokenAssignment ::
          TokenInteger(2) ::
          Nil
        }
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

      "should reject bad integer literals" in {
        invalidate("123abc")
        invalidate("123z")
      }
    }

    // Overlapping tokens
    "lexing overlapping tokens (stem groups)" should {
      "should consume tokens with common beginnings" in {
        validate("a:1") { TokenId("a") :: TokenColon :: TokenInteger(1) :: Nil }
        validate("a:=1") { TokenId("a") :: TokenAssignment :: TokenInteger(1) :: Nil }

        validate("1 > 2") { TokenInteger(1) :: TokenGreater :: TokenInteger(2) :: Nil }
        validate("1 >= 2") { TokenInteger(1) :: TokenGreaterOrEqual :: TokenInteger(2) :: Nil }

        validate("1 < 2") { TokenInteger(1) :: TokenLess :: TokenInteger(2) :: Nil }
        validate("1 <= 2") { TokenInteger(1) :: TokenLessOrEqual :: TokenInteger(2) :: Nil }
        validate("1 <> 2") { TokenInteger(1) :: TokenInequality :: TokenInteger(2) :: Nil }

        // Division operator overlaps with comments
        validate("1/2") { TokenInteger(1) :: TokenDivision :: TokenInteger(2) :: Nil }
        validate("2 / 1") { TokenInteger(2) :: TokenDivision :: TokenInteger(1) :: Nil }
      }
    }

    // All symbols
    "lexing symbols" should {
      "should correctly identify all symbols" in {
        validate("{") { TokenBraceOpen :: Nil }
        validate("}") { TokenBraceClose :: Nil }
        validate("[") { TokenBracketOpen :: Nil }
        validate("]") { TokenBracketClose :: Nil }
        validate("(") { TokenParenOpen :: Nil }
        validate(")") { TokenParenClose :: Nil }

        validate(".") { TokenDereference :: Nil }
        validate(",") { TokenFieldSep :: Nil }
        validate(";") { TokenSemicolon :: Nil }
        validate("*") { TokenMultiplication :: Nil }
        validate("/") { TokenDivision :: Nil }
        validate("+") { TokenAddition :: Nil }
        validate("-") { TokenSubtraction :: Nil }
        validate("=") { TokenEquality :: Nil }

        validate(":") { TokenColon :: Nil }
        validate(":=") { TokenAssignment :: Nil }

        validate("<") { TokenLess :: Nil }
        validate("<=") { TokenLessOrEqual :: Nil }
        validate("<>") { TokenInequality :: Nil }

        validate(">") { TokenGreater :: Nil }
        validate(">=") { TokenGreaterOrEqual :: Nil }

        validate("&") { TokenAnd :: Nil }
        validate("|") { TokenOr :: Nil }
      }
    }

    // All keywords
    "lexing keywords" should {
      "should correctly identify all symbols" in {
        validate("array") { TokenKeyArray :: Nil }
        validate("break") { TokenKeyBreak :: Nil }
        validate("do") { TokenKeyDo :: Nil }
        validate("else") { TokenKeyElse :: Nil }
        validate("end") { TokenKeyEnd :: Nil }
        validate("for") { TokenKeyFor :: Nil }
        validate("function") { TokenKeyFunction :: Nil }
        validate("if") { TokenKeyIf :: Nil }
        validate("in") { TokenKeyIn :: Nil }
        validate("let") { TokenKeyLet :: Nil }
        validate("nil") { TokenKeyNil :: Nil }
        validate("of") { TokenKeyOf :: Nil }
        validate("then") { TokenKeyThen :: Nil }
        validate("to") { TokenKeyTo :: Nil }
        validate("type") { TokenKeyType :: Nil }
        validate("var") { TokenKeyVar :: Nil }
        validate("while") { TokenKeyWhile :: Nil }
      }
    }

    // Identifiers
    "lexing identifiers" should {
      "should collect identifiers" in {
        validate("when") { TokenId("when") :: Nil }
        validate("id") { TokenId("id") :: Nil }
        validate("id13") { TokenId("id13") :: Nil }
        validate("i1d3") { TokenId("i1d3") :: Nil }
        validate("id13_") { TokenId("id13_") :: Nil }
        validate("id13__") { TokenId("id13__") :: Nil }
        validate("id_13") { TokenId("id_13") :: Nil }
        validate("id__13") { TokenId("id__13") :: Nil }
      }

      "should reject bad identifiers" in {
        invalidate("_id")
        invalidate("_1d")
        invalidate("_12")
        invalidate("12_")
        invalidate("1d_")
        invalidate("1_2")
        invalidate("1_d")
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

    "lexing full programs" should {
      "lex all parts" in {
        """
        |
        |""".stripMargin
      }
    }
  }
}
