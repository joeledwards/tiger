package com.buzuli.tiger

abstract class TigerError extends Throwable {
  def message: String
  override def getMessage: String = message
  override def getLocalizedMessage: String = message
}

case class DecodeError(message: String) extends TigerError
case class LexerError(msg: String, line: Int, column: Int) extends TigerError {
  def message: String = s"${msg} at line=${line} col=${column}"
}
case class ParserError(message: String) extends TigerError
case class TypeError(message: String) extends TigerError
case class InterpreterError(message: String) extends TigerError

