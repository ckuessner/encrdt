package de.ckuessner
package encrdt.benchmarks.mock.insecure

import encrdt.benchmarks.mock.IntermediarySizeInfo
import encrdt.benchmarks.todolist.ToDoEntry
import encrdt.crdts.DeltaAddWinsLastWriterWinsMap
import encrdt.crdts.DeltaAddWinsLastWriterWinsMap.DeltaAddWinsLastWriterWinsMapLattice

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, readFromArray, writeToArray}

import java.util.UUID

class AlternativeInsecureToDoListIntermediary(val intermediaryReplicaId: String)(
    implicit val stateJsonCodec: JsonValueCodec[DeltaAddWinsLastWriterWinsMapLattice[UUID, ToDoEntry]]
) extends IntermediarySizeInfo {
  private val crdt = new DeltaAddWinsLastWriterWinsMap[UUID, ToDoEntry]("intermediary")

  def receive(serializedDelta: Array[Byte]): Unit = {
    val delta: DeltaAddWinsLastWriterWinsMapLattice[UUID, ToDoEntry] = readFromArray(serializedDelta)
    crdt.merge(delta)
  }

  def sizeInBytes: Long = writeToArray(crdt.state).length

  override val encDeltaCausalityInfoSizeInBytes: Long = 0L

  override def rawDeltasSizeInBytes: Long = sizeInBytes

  override def numberStoredDeltas: Int = 1
}
