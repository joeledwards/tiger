package com.buzuli.tiger

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}

sealed trait ConsumeResult
case object ConsumeContinue extends ConsumeResult
case class ConsumeContinueWithNewConsumer(newConsumer: LexConsumer) extends ConsumeResult
case class ConsumeComplete(token: Option[Token], char: Option[Char] = None) extends ConsumeResult
case class ConsumeReject(error: TigerError) extends ConsumeResult

trait LexConsumer {
  def line: Int
  def column: Int
  def input: String

  def nom(char: Char): ConsumeResult
  def end(): ConsumeResult = ConsumeReject(LexerError(s"Unexpected end of token [${input}]", line, column))
}

object Lexer {
  def create: Flow[Char, Token, NotUsed] = {
    var line: Int = 0
    var column: Int = 0
    var context: Option[LexConsumer] = None

    def consumeChar(char: Char): List[Token] = {
      (context, char) match {
        case (Some(consumer), c) => consumer.nom(c) match {
          // Feed the active consumer
          case ConsumeReject(error) => throw error
          case completion: ConsumeComplete => {
            context = None
            completion match {
              case ConsumeComplete(None, None) => Nil
              case ConsumeComplete(Some(token), None) => token :: Nil
              case ConsumeComplete(None, Some(extraChar)) => consumeChar(extraChar)
              case ConsumeComplete(Some(token), Some(extraChar)) => token :: consumeChar(extraChar)
            }
          }
          case ConsumeContinueWithNewConsumer(newConsumer) => {
            context = Some(newConsumer)
            Nil
          }
          case ConsumeContinue => Nil
        }
        case (None, '"') => {
          context = Some(LexString(line, column))
          Nil
        }
        case (None, '{') => TokenBraceOpen :: Nil
        case (None, '}') => TokenBraceClose :: Nil
        case (None, '[') => TokenBracketOpen :: Nil
        case (None, ']') => TokenBracketClose :: Nil
        case (None, '(') => TokenParenOpen :: Nil
        case (None, ')') => TokenParenClose :: Nil

        case (None, '.') => TokenDereference :: Nil
        case (None, ',') => TokenFieldSep :: Nil
        case (None, ';') => TokenSemicolon :: Nil
        case (None, '*') => TokenMultiplication :: Nil
        case (None, '+') => TokenAddition :: Nil
        case (None, '-') => TokenSubtraction :: Nil
        case (None, '=') => TokenEquality :: Nil

        case (None, ':') => {
          context = Some(StemGroup(char, TokenColon, line, column))
          Nil
        }

        case (None, '<') => {
          context = Some(StemGroup(char, TokenLess, line, column))
          Nil
        }

        case (None, '>') => {
          context = Some(StemGroup(char, TokenGreater, line, column))
          Nil
        }

        case (None, '/') => {
          context = Some(StemGroup(char, TokenDivision, line, column))
          Nil
        }

        case (None, '&') => TokenAnd :: Nil
        case (None, '|') => TokenOr :: Nil

        case (None, c) if c.isWhitespace => {
          // Ignore whitespace
          Nil
        }
        case (None, c) if c.isDigit => {
          context = Some(LexInteger(char, line, column))
          Nil
        }
        case (None, c) if (c.isLetter) => {
          context = Some(LexId(char, line, column))
          Nil
        }
        case _ => throw LexerError(s"Unsupported character '$char'", line, column)
      }
    }

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

case class StemGroup(char: Char, token: Token, line: Int, column: Int) extends LexConsumer {
  private var builder = new StringBuilder += char

  override def input = builder.toString

  override def nom(char: Char): ConsumeResult = {
    builder += char

    (token, char) match {
      case (TokenColon, '=') => ConsumeComplete(Some(TokenAssignment))
      case (TokenColon, _) => ConsumeComplete(Some(TokenColon), Some(char))
      case (TokenLess, '>') => ConsumeComplete(Some(TokenInequality))
      case (TokenLess, '=') => ConsumeComplete(Some(TokenLessOrEqual))
      case (TokenLess, _) => ConsumeComplete(Some(TokenLess), Some(char))
      case (TokenGreater, '=') => ConsumeComplete(Some(TokenGreaterOrEqual))
      case (TokenGreater, _) => ConsumeComplete(Some(TokenGreater), Some(char))
      case (TokenDivision, '*') => ConsumeContinueWithNewConsumer(LexComment(line, column))
      case (TokenDivision, _) => ConsumeComplete(Some(TokenDivision), Some(char))
      case _ => ConsumeReject(LexerError(s"""Unsupported token "${builder.toString}"""", line, column))
    }
  }

  override def end(): ConsumeResult = {
    // NOTE: if we ever stem on a value which is not a valid stand-alone token,
    //       we will need to identify it here and kick back a LexerError
    ConsumeComplete(Some(token))
  }
}

case class LexInteger(char: Char, line: Int, column: Int) extends LexConsumer {
  private var builder = new StringBuilder += char

  override def input = builder.toString

  override def nom(char: Char): ConsumeResult = char match {
    case c if c.isDigit => {
      builder += c
      ConsumeContinue
    }
    case c if c.isLetter => {
      builder += c
      ConsumeReject(LexerError(s"Invalid numeric constant ${builder}", line, column))
    }
    case c => {
      ConsumeComplete(Some(intValue), Some(c))
    }
  }

  override def end(): ConsumeResult = {
    ConsumeComplete(Some(intValue)) }

  private def intValue: Token = TokenInteger(builder.toString.toInt)
}

case class LexId(char: Char, line: Int, column: Int) extends LexConsumer {
  private var builder = new StringBuilder += char

  override def input = builder.toString

  override def nom(char: Char): ConsumeResult = char match {
    case c if (c.isLetterOrDigit || c == '_') => {
      builder += c
      ConsumeContinue
    }
    case c => {
      // Identify keywords
      val token: Token = keywordLense()
      ConsumeComplete(Some(token), Some(c))
    }
  }

  override def end(): ConsumeResult = ConsumeComplete(Some(keywordLense()))

  private def keywordLense(): Token = builder.toString match {
    case "array" => TokenKeyArray
    case "break" => TokenKeyBreak
    case "do" => TokenKeyDo
    case "else" => TokenKeyElse
    case "end" => TokenKeyEnd
    case "for" => TokenKeyFor
    case "function" => TokenKeyFunction
    case "if" => TokenKeyIf
    case "in" => TokenKeyIn
    case "let" => TokenKeyLet
    case "nil" => TokenKeyNil
    case "of" => TokenKeyOf
    case "then" => TokenKeyThen
    case "to" => TokenKeyTo
    case "type" => TokenKeyType
    case "var" => TokenKeyVar
    case "while" => TokenKeyWhile
    case s => TokenId(s)
  }
}

sealed trait EscapeMode
case object EscapeOff extends EscapeMode
case object EscapeStart extends EscapeMode
case object EscapeControl extends EscapeMode
case object EscapeSpace extends EscapeMode
case class EscapeAscii(codes: List[Char]) extends EscapeMode

case class LexString(line: Int, column: Int) extends LexConsumer {
  private var raw = new StringBuilder += '"'
  private var builder = new StringBuilder
  private var escape: EscapeMode = EscapeOff

  override def input: String = raw.toString

  override def nom(char: Char): ConsumeResult = {
    raw += char

    (escape, char) match {
      case (EscapeOff, '"') => {
        // Complete the string if we are not escaping
        ConsumeComplete(Some(TokenString(builder.toString)))
      }
      case (EscapeOff, '\\') => {
        // Begin escaping if we are not already escaping
        escape = EscapeStart
        ConsumeContinue
      }
      case (EscapeOff, c) => {
        // Add characters if we are not escaping
        builder += c
        ConsumeContinue
      }
      case (EscapeStart, 'n') => {
        builder += '\n'
        escape = EscapeOff
        ConsumeContinue
      }
      case (EscapeStart, 't') => {
        builder += '\t'
        escape = EscapeOff
        ConsumeContinue
      }
      case (EscapeStart, '^') => {
        // Begin consuming a control character
        escape = EscapeControl
        ConsumeContinue
      }
      case (EscapeControl, 'c') => {
        // Emit the ^C control character
        builder += 3.toChar
        escape = EscapeOff
        ConsumeContinue
      }
      case (EscapeStart, x) if x.isDigit => {
        // Start consuming an ascii code
        escape = EscapeAscii(x :: Nil)
        ConsumeContinue
      }
      case (EscapeAscii(x :: Nil), y) if y.isDigit => {
        // Continue consuming an ascii code
        escape = EscapeAscii(x :: y :: Nil)
        ConsumeContinue
      }
      case (EscapeAscii(x :: y :: Nil), z) if z.isDigit => {
        // Emit a complete ascii code
        builder += s"$x$y$z".toInt.toChar
        escape = EscapeOff
        ConsumeContinue
      }
      case (EscapeStart, '"') => {
        builder += '"'
        escape = EscapeOff
        ConsumeContinue
      }
      case (EscapeStart, '\\') => {
        builder += '\\'
        escape = EscapeOff
        ConsumeContinue
      }
      case (EscapeStart, c) if c.isWhitespace => {
        // Start ignoring spaces
        escape = EscapeSpace
        ConsumeContinue
      }
      case (EscapeSpace, c) if c.isWhitespace => {
        // Continue ignoring spaces
        ConsumeContinue
      }
      case (EscapeSpace, '\\') => {
        // Done ignoring spaces
        escape = EscapeOff
        ConsumeContinue
      }
      case _ => {
        escape match {
          case EscapeOff => ConsumeReject(LexerError(s"""Invalid string literal "${raw}"""", line, column))
          case _ => ConsumeReject(LexerError(s"""Invalid escape sequence in string literal "${raw}"""", line, column))
        }
      }
    }
  }
}

case class LexComment(line: Int, column: Int) extends LexConsumer {
  private var raw = new StringBuilder ++= "/*"
  private var builder = new StringBuilder
  private var endSignaled = false

  override def input: String = raw.toString

  override def nom(char: Char): ConsumeResult = {
    raw += char

    (endSignaled, char) match {
      case (true, '/') => ConsumeComplete(None)
      case (_, '*') => {
        endSignaled = true
        ConsumeContinue
      }
      case (_, _) => {
        endSignaled = false
        ConsumeContinue
      }
    }
  }
}
