package com.buzuli.tiger

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl._

import scala.concurrent.{ExecutionContext, Future}

object Tiger {
  implicit val ec = ExecutionContext.Implicits.global
  implicit val system = ActorSystem("tiger")
  import system.dispatcher
  implicit val materializer = ActorMaterializer

  def run(source: String): Future[Int] = {
    val sourceFile = Paths.get(source)

    // Looks so peaceful...
    FileIO.fromPath(sourceFile)
      .via(Decoder.create)
      .via(Lexer.create)
      .via(Parser.create)
      .via(TypeChecker.create)
      .to(Interpreter.create)
      .run()
      .map(_ => 0)
  }
}
