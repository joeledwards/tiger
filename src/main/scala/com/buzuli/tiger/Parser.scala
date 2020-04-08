package com.buzuli.tiger

import akka.stream.{FlowShape, Graph}

case class IrNode(token: Token) {
}

object Parser {
  def create: Graph[FlowShape[Token, IrNode], Nothing] = {

  }
}
