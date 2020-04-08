package com.buzuli.tiger

import akka.NotUsed
import akka.stream.{FlowShape, Graph}
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString

object Decoder {
  def create: Graph[FlowShape[ByteString, Char], NotUsed] = {
    Flow[ByteString]
      .map(_.decodeString("utf-8"))
      .map(_.toCharArray)
      .flatMapConcat(s => Source.fromIterator(() => s.iterator))
  }
}
