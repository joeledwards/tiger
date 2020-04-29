package com.buzuli.tiger

import akka.NotUsed
import akka.stream.scaladsl.Flow

object TypeChecker {
  def create: Flow[IrNode, AstNode, NotUsed] = {
    // TODO: build it

    Flow[IrNode].map(n => AstNode(n.token))
  }
}
