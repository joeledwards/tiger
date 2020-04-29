package com.buzuli.tiger

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.stream.{FlowShape, Graph}

case class IrNode(token: Token) {
}

object Parser {
  def create: Flow[Token, IrNode, NotUsed] = {
    // TODO: build it

    Flow[Token].map(IrNode(_))
  }
}
