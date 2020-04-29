package com.buzuli.tiger

sealed trait Token

case object TokenBraceOpen extends Token
case object TokenBraceClose extends Token
case object TokenBracketOpen extends Token
case object TokenBracketClose extends Token
case object TokenParenOpen extends Token
case object TokenParenClose extends Token
case object TokenColon extends Token
case object TokenAssignment extends Token
case object TokenDereference extends Token
case object TokenFieldSep extends Token
case object TokenSemicolon extends Token
case object TokenMultiplication extends Token
case object TokenDivision extends Token
case object TokenAddition extends Token
case object TokenSubtraction extends Token
case object TokenEquality extends Token
case object TokenInequality extends Token
case object TokenGreater extends Token
case object TokenLess extends Token
case object TokenGreaterOrEqual extends Token
case object TokenLessOrEqual extends Token
case object TokenAnd extends Token
case object TokenOr extends Token

case object TokenKeyArray extends Token
case object TokenKeyBreak extends Token
case object TokenKeyDo extends Token
case object TokenKeyElse extends Token
case object TokenKeyEnd extends Token
case object TokenKeyFor extends Token
case object TokenKeyFunction extends Token
case object TokenKeyIf extends Token
case object TokenKeyIn extends Token
case object TokenKeyLet extends Token
case object TokenKeyNil extends Token
case object TokenKeyOf extends Token
case object TokenKeyThen extends Token
case object TokenKeyTo extends Token
case object TokenKeyType extends Token
case object TokenKeyVar extends Token
case object TokenKeyWhile extends Token

case class TokenId(value: String) extends Token
case class TokenInteger(value: Int) extends Token
case class TokenString(value: String) extends Token
