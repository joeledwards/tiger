package com.buzuli.tiger

import akka.stream.scaladsl
import akka.stream.scaladsl.{Flow, Sink}

import scala.concurrent.{ExecutionContext, Future, Promise}

object Interpreter {
  def create(implicit ec: ExecutionContext): Sink[AstNode, Future[Int]] = {
    // TODO: build it

    Sink
      .fold[List[AstNode], AstNode](Nil)((nodes, node) => nodes :+ node)
      .mapMaterializedValue[Future[Int]](_.flatMap(runProgram(_)))
  }

  def runProgram(tree: List[AstNode]): Future[Int] = {
    val promise = Promise[Int]

    promise.future
  }
}
