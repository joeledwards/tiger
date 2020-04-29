package com.buzuli.tiger

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}

object Lexer {
  def create: Flow[Char, Token, NotUsed] = {
    var line: Int = 0
    var column: Int = 0
    var context: Option[LexConsumer] = None

    def consumeChar(char: Char): List[Token] = {
      (context, char) match {
        case (Some(ctxt), c) => ctxt.nom(c) match {
          // Continue processing contextual consumers
          case ConsumeReject(error) => throw error
          case ConsumeComplete(token, Some(extraChar)) => {
            // Handle a rejected (peaked) character.
            // Since a token was emitted, we clear context.
            context = None
            token :: consumeChar(extraChar)
          }
          case ConsumeComplete(token, None) => {
            context = None
            token :: Nil
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
        case (None, '/') => TokenDivision :: Nil
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
          case (None, Some(ctxt)) => {
            // We reached the end of input, but we still have active context
            ctxt.end() match {
              case ConsumeComplete(token, _) => token :: Nil
              case ConsumeReject(error) => throw error
              case _ => {
                throw LexerError(s"Unexpected end of stream with active context ${ctxt}", line, column)
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

sealed trait ConsumeResult
case object ConsumeContinue extends ConsumeResult
case class ConsumeComplete(token: Token, char: Option[Char] = None) extends ConsumeResult
case class ConsumeReject(error: TigerError) extends ConsumeResult

trait LexConsumer {
  def line: Int
  def column: Int
  def input: String

  def nom(char: Char): ConsumeResult
  def end(): ConsumeResult = ConsumeReject(LexerError(s"Unexpected end of token [${input}]", line, column))
}

case class StemGroup(char: Char, token: Token, line: Int, column: Int) extends LexConsumer {
  private var builder = new StringBuilder += char

  override def input = builder.toString

  override def nom(char: Char): ConsumeResult = {
    builder += char

    (token, char) match {
      case (TokenColon, '=') => ConsumeComplete(TokenAssignment)
      case (TokenColon, _) => ConsumeComplete(TokenColon, Some(char))
      case (TokenLess, '>') => ConsumeComplete(TokenInequality)
      case (TokenLess, '=') => ConsumeComplete(TokenLessOrEqual)
      case (TokenLess, _) => ConsumeComplete(TokenLess, Some(char))
      case (TokenGreater, '=') => ConsumeComplete(TokenGreaterOrEqual)
      case (TokenGreater, _) => ConsumeComplete(TokenGreater, Some(char))
      case _ => ConsumeReject(LexerError(s"""Unsupported token "${builder.toString}"""", line, column))
    }
  }

  override def end(): ConsumeResult = {
    // NOTE: if we ever stem on a value which is not a valid stand-alone token,
    //       we will need to identify it here and kick back a LexerError
    ConsumeComplete(token)
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
      ConsumeComplete(TokenInteger(builder.toString.toInt), Some(c))
    }
  }

  override def end(): ConsumeResult = {
    ConsumeComplete(TokenInteger(builder.toString.toInt))
  }
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
      val token: Token = builder.toString match {
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
      ConsumeComplete(token, Some(c))
    }
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
        ConsumeComplete(TokenString(builder.toString))
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
          case EscapeOff => ConsumeReject(LexerError(s"""Invalid string literal "${builder}"""", line, column))
          case _ => ConsumeReject(LexerError(s"""Invalid escape sequence in string literal "${builder}"""", line, column))
        }
      }
    }
  }
}
