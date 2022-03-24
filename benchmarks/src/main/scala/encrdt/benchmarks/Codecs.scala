package de.ckuessner
package encrdt.benchmarks

import encrdt.benchmarks.todolist.ToDoEntry
import encrdt.causality.DotStore.{Dot, DotSet}
import encrdt.causality.impl.Defs.{Id, Time}
import encrdt.causality.impl.{ArrayCausalContext, ArrayRanges}
import encrdt.causality.{CausalContext, LamportClock}
import encrdt.crdts.{AddWinsLastWriterWinsMap, DeltaAddWinsLastWriterWinsMap}
import encrdt.util.CodecConfig.relaxedJsonCodecConfig

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonKeyCodec, JsonReader, JsonValueCodec, JsonWriter}
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

import java.time.Instant
import java.util.UUID

object Codecs {
  implicit val awlwwmapJsonCodec: JsonValueCodec[AddWinsLastWriterWinsMap.LatticeType[String, String]] =
    JsonCodecMaker.make(relaxedJsonCodecConfig)

  implicit val dotMapAsSetCodec: JsonValueCodec[Set[(Dot, (String, (Instant, String)))]] =
    JsonCodecMaker.make(relaxedJsonCodecConfig)

  implicit val dotSetCodec: JsonValueCodec[DotSet] =
    JsonCodecMaker.make(relaxedJsonCodecConfig)

  implicit val causalContextCodec: JsonValueCodec[CausalContext] = new JsonValueCodec[CausalContext] {
    private val optimizedArrayCausalContextCodec: JsonValueCodec[Map[Id, Array[Time]]] = JsonCodecMaker.make

    override def decodeValue(in: JsonReader, default: CausalContext): CausalContext = {
      CausalContext(ArrayCausalContext(
        optimizedArrayCausalContextCodec.decodeValue(in, Map.empty).map {
          case (id, times) => id -> ArrayRanges(times, times.length)
        }
      ))
    }

    override def encodeValue(x: CausalContext, out: JsonWriter): Unit = {
      optimizedArrayCausalContextCodec.encodeValue(
        x.acc.internal.map { case (id, ranges) =>
          id -> {
            if (ranges.used == ranges.inner.length) ranges.inner
            else Array.copyOf(ranges.inner, ranges.used)
          }
        },
        out
      )
    }

    override def nullValue: CausalContext = CausalContext()
  }

  implicit val dotMapCodec: JsonValueCodec[Map[Dot, (String, (Instant, String))]] =
    new JsonValueCodec[Map[Dot, (String, (Instant, String))]] {
      override def decodeValue(
          in: JsonReader,
          default: Map[Dot, (String, (Instant, String))]
      ): Map[Dot, (String, (Instant, String))] =
        dotMapAsSetCodec.decodeValue(in, Set.empty).toMap

      override def encodeValue(x: Map[Dot, (String, (Instant, String))], out: JsonWriter): Unit =
        dotMapAsSetCodec.encodeValue(x.toSet, out)

      override def nullValue: Map[Dot, (String, (Instant, String))] =
        Map.empty
    }

  implicit val deltaAwlwwmapJsonCodec: JsonValueCodec[DeltaAddWinsLastWriterWinsMap.StateType[String, String]] =
    JsonCodecMaker.make(relaxedJsonCodecConfig)

  implicit val lamportClockCodec: JsonKeyCodec[Dot] = new JsonKeyCodec[Dot] {
    override def decodeKey(in: JsonReader): Dot = {
      val inputString = in.readKeyAsString()
      val index       = inputString.indexOf('@')
      LamportClock(inputString.substring(0, index).toLong, inputString.substring(index + 1))
    }

    override def encodeKey(x: Dot, out: JsonWriter): Unit = out.writeKey(s"${x.time}@${x.replicaId}")
  }

  implicit val toDoMapCodec: JsonValueCodec[DeltaAddWinsLastWriterWinsMap.StateType[UUID, ToDoEntry]] =
    JsonCodecMaker.make(relaxedJsonCodecConfig)
}
