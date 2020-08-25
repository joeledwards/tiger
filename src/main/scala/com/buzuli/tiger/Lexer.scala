package com.buzuli.tiger

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}

sealed trait LexConsumeResult
case object LexConsumeContinue extends LexConsumeResult
case class LexConsumeContinueWithNewConsumer(newConsumer: LexConsumer) extends LexConsumeResult
case class LexConsumeComplete(token: Option[Token], char: Option[Char] = None) extends LexConsumeResult
case class LexConsumeReject(error: TigerError) extends LexConsumeResult

trait LexConsumer {
  def line: Int
  def column: Int
  def input: String

  def nom(char: Char): LexConsumeResult
  def end(): LexConsumeResult = LexConsumeReject(LexerError(s"Unexpected end of token [${input}]", line, column))
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
          case LexConsumeReject(error) => throw error
          case completion: LexConsumeComplete => {
            context = None
            completion match {
              case LexConsumeComplete(None, None) => Nil
              case LexConsumeComplete(Some(token), None) => token :: Nil
              case LexConsumeComplete(None, Some(extraChar)) => consumeChar(extraChar)
              case LexConsumeComplete(Some(token), Some(extraChar)) => token :: consumeChar(extraChar)
            }
          }
          case LexConsumeContinueWithNewConsumer(newConsumer) => {
            context = Some(newConsumer)
            Nil
          }
          case LexConsumeContinue => Nil
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
              case LexConsumeComplete(Some(token), _) => token :: Nil
              case LexConsumeComplete(None, _) => Nil
              case LexConsumeReject(error) => throw error
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

  override def nom(char: Char): LexConsumeResult = {
    builder += char

    (token, char) match {
      case (TokenColon, '=') => LexConsumeComplete(Some(TokenAssignment))
      case (TokenColon, _) => LexConsumeComplete(Some(TokenColon), Some(char))
      case (TokenLess, '>') => LexConsumeComplete(Some(TokenInequality))
      case (TokenLess, '=') => LexConsumeComplete(Some(TokenLessOrEqual))
      case (TokenLess, _) => LexConsumeComplete(Some(TokenLess), Some(char))
      case (TokenGreater, '=') => LexConsumeComplete(Some(TokenGreaterOrEqual))
      case (TokenGreater, _) => LexConsumeComplete(Some(TokenGreater), Some(char))
      case (TokenDivision, '*') => LexConsumeContinueWithNewConsumer(LexComment(line, column))
      case (TokenDivision, _) => LexConsumeComplete(Some(TokenDivision), Some(char))
      case _ => LexConsumeReject(LexerError(s"""Unsupported token "${builder.toString}"""", line, column))
    }
  }

  override def end(): LexConsumeResult = {
    // NOTE: if we ever stem on a value which is not a valid stand-alone token,
    //       we will need to identify it here and kick back a LexerError
    LexConsumeComplete(Some(token))
  }
}

case class LexInteger(char: Char, line: Int, column: Int) extends LexConsumer {
  private var builder = new StringBuilder += char

  override def input = builder.toString

  override def nom(char: Char): LexConsumeResult = char match {
    case c if c.isDigit => {
      builder += c
      LexConsumeContinue
    }
    case c if c.isLetter => {
      builder += c
      LexConsumeReject(LexerError(s"Invalid numeric constant ${builder}", line, column))
    }
    case c => {
      LexConsumeComplete(Some(intValue), Some(c))
    }
  }

  override def end(): LexConsumeResult = {
    LexConsumeComplete(Some(intValue)) }

  private def intValue: Token = TokenInteger(builder.toString.toInt)
}

case class LexId(char: Char, line: Int, column: Int) extends LexConsumer {
  private var builder = new StringBuilder += char

  override def input = builder.toString

  override def nom(char: Char): LexConsumeResult = char match {
    case c if (c.isLetterOrDigit || c == '_') => {
      builder += c
      LexConsumeContinue
    }
    case c => {
      // Identify keywords
      val token: Token = keywordLense()
      LexConsumeComplete(Some(token), Some(c))
    }
  }

  override def end(): LexConsumeResult = LexConsumeComplete(Some(keywordLense()))

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

  override def nom(char: Char): LexConsumeResult = {
    raw += char

    (escape, char) match {
      case (EscapeOff, '"') => {
        // Complete the string if we are not escaping
        LexConsumeComplete(Some(TokenString(builder.toString)))
      }
      case (EscapeOff, '\\') => {
        // Begin escaping if we are not already escaping
        escape = EscapeStart
        LexConsumeContinue
      }
      case (EscapeOff, c) => {
        // Add characters if we are not escaping
        builder += c
        LexConsumeContinue
      }
      case (EscapeStart, 'n') => {
        builder += '\n'
        escape = EscapeOff
        LexConsumeContinue
      }
      case (EscapeStart, 't') => {
        builder += '\t'
        escape = EscapeOff
        LexConsumeContinue
      }
      case (EscapeStart, '^') => {
        // Begin consuming a control character
        escape = EscapeControl
        LexConsumeContinue
      }
      case (EscapeControl, 'c') => {
        // Emit the ^C control character
        builder += 3.toChar
        escape = EscapeOff
        LexConsumeContinue
      }
      case (EscapeStart, x) if x.isDigit => {
        // Start consuming an ascii code
        escape = EscapeAscii(x :: Nil)
        LexConsumeContinue
      }
      case (EscapeAscii(x :: Nil), y) if y.isDigit => {
        // Continue consuming an ascii code
        escape = EscapeAscii(x :: y :: Nil)
        LexConsumeContinue
      }
      case (EscapeAscii(x :: y :: Nil), z) if z.isDigit => {
        // Emit a complete ascii code
        builder += s"$x$y$z".toInt.toChar
        escape = EscapeOff
        LexConsumeContinue
      }
      case (EscapeStart, '"') => {
        builder += '"'
        escape = EscapeOff
        LexConsumeContinue
      }
      case (EscapeStart, '\\') => {
        builder += '\\'
        escape = EscapeOff
        LexConsumeContinue
      }
      case (EscapeStart, c) if c.isWhitespace => {
        // Start ignoring spaces
        escape = EscapeSpace
        LexConsumeContinue
      }
      case (EscapeSpace, c) if c.isWhitespace => {
        // Continue ignoring spaces
        LexConsumeContinue
      }
      case (EscapeSpace, '\\') => {
        // Done ignoring spaces
        escape = EscapeOff
        LexConsumeContinue
      }
      case _ => {
        escape match {
          case EscapeOff => LexConsumeReject(LexerError(s"""Invalid string literal "${raw}"""", line, column))
          case _ => LexConsumeReject(LexerError(s"""Invalid escape sequence in string literal "${raw}"""", line, column))
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

  override def nom(char: Char): LexConsumeResult = {
    raw += char

    (endSignaled, char) match {
      case (true, '/') => LexConsumeComplete(None)
      case (_, '*') => {
        endSignaled = true
        LexConsumeContinue
      }
      case (_, _) => {
        endSignaled = false
        LexConsumeContinue
      }
    }
  }
}
