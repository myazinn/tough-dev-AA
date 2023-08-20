package com.home.avro.schema.utils

import java.nio.ByteBuffer

import scala.util.Try

object SchemaId:
  def apply(data: Array[Byte]): Option[Int] =
    Try:
      val bb = ByteBuffer.allocate(4)
      bb.put(data.slice(1, 5))
      bb.getInt
    .toOption
