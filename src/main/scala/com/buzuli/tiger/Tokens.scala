package com.buzuli.tiger

/**
 * Lexeme => actual value
 * Token => lexeme and associated metadata
 */

case class Token(
  text: String,
  line: Int,
  column: Int
)

sealed trait TokenType

case object SoloToken extends TokenType
case class TokenStart(end: Token) extends TokenType
case class TokenEnd(start: Token) extends TokenType

sealed trait TokenDescriptor
case class TextDescriptor(text: String) extends TokenDescriptor
case class PatternDescriptor(text: String) extends TokenDescriptor

sealed abstract class Token(descriptor: TokenDescriptor, container: TokenType = SoloToken)

case object OpenString extends Token(TextDescriptor("\""), TokenStart(CloseString))
case object CloseString extends Token(TextDescriptor("\""), TokenEnd(OpenString))
case object OpenParen extends Token(TextDescriptor("("), TokenStart(CloseParen))
case object CloseParen extends Token(TextDescriptor(")"), TokenEnd(OpenParen))
case object OpenBracket extends Token(TextDescriptor("["), TokenStart(CloseBracket))
case object CloseBracket extends Token(TextDescriptor("]"), TokenEnd(OpenBracket))
case object OpenBrace extends Token(TextDescriptor("{"), TokenStart(CloseBrace))
case object CloseBrace extends Token(TextDescriptor("}"), TokenEnd(OpenBrace))

case object Colon extends Token(TextDescriptor(":"))
case object Assignment extends Token(TextDescriptor(":="))
case object Dereference extends Token(TextDescriptor("."))
case object FieldSep extends Token(TextDescriptor(","))
case object Semicolon extends Token(TextDescriptor(";"))
case object Multiplication extends Token(TextDescriptor("*"))
case object Division extends Token(TextDescriptor("/"))
case object Addition extends Token(TextDescriptor("+"))
case object Subtraction extends Token(TextDescriptor("-"))
case object Equality extends Token(TextDescriptor("="))
case object Inequality extends Token(TextDescriptor("<>"))
case object Greater extends Token(TextDescriptor(">"))
case object Less extends Token(TextDescriptor("<"))
case object GreaterOrEqual extends Token(TextDescriptor(">="))
case object LessOrEqual extends Token(TextDescriptor("<="))
case object And extends Token(TextDescriptor("&"))
case object Or extends Token(TextDescriptor("|"))

sealed abstract class EscapeSequence(value: String)
case object Newline extends EscapeSequence("\\n")
case object Tab extends EscapeSequence("\\t")
case object Control extends EscapeSequence("\\^c")
case object DoubleQuote extends EscapeSequence("\\\"")
case object Backslash extends EscapeSequence("\\\\")
case object AsciiCode extends EscapeSequence("\\ddd") // Must support patterns
case object Whitespace extends EscapeSequence("\\s...s\\") // Must support patterns

class TokenContext {
  // TODO: Start with all and reduce as each character is consumed
  // if multiple still present, continue
  // if one is present, emit token
  // if zero are present, emit error (unknown token)
  var lexemeSet: Set[Lexeme] = Set.empty
  var text: StringBuilder = new StringBuilder

  def nom(c: Char): LexState = {
    text += c
    lexemeSet = lexemeSet.filter(l => l.nom(c))
    lexemeSet.size match {
      case 0 => LexFailed(new LexerError(s"Unknown token '${String.copyValueOf(text.toArray)}'"), context)
      case 1 => LexToken(, context)
      case _ => LexContinue(context)
    }
  }
}

sealed trait LexState
case class LexFailed(cause: Throwable, context: TokenContext)
case class LexToken(token: Token, context: TokenContext)
case class LexContinue(context: TokenContext)

class LexerError(message: String) extends Throwable(message)
