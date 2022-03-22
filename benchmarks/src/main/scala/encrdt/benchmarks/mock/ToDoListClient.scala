package de.ckuessner
package encrdt.benchmarks.mock

import encrdt.benchmarks.Codecs.{causalContextCodec, dotSetCodec, toDoMapCodec}
import encrdt.benchmarks.mock.ToDoListClient.{ToDoMapLattice, mergeDecryptedDeltas}
import encrdt.benchmarks.todolist.ToDoEntry
import encrdt.crdts.DeltaAddWinsLastWriterWinsMap
import encrdt.crdts.DeltaAddWinsLastWriterWinsMap.{DeltaAddWinsLastWriterWinsMapLattice, timestampedValueLattice}
import encrdt.encrypted.deltabased.{DecryptedDeltaGroup, EncryptedDeltaGroup, TrustedReplica, UntrustedReplica}
import encrdt.lattices.{Causal, SemiLattice}
import com.google.crypto.tink.Aead
import de.ckuessner.encrdt.causality.CausalContext

import java.util.UUID
import scala.collection.mutable

class ToDoListClient(replicaId: String,
                     crdt: DeltaAddWinsLastWriterWinsMap[UUID, ToDoEntry],
                     aead: Aead,
                     private val intermediary: UntrustedReplica
                    ) extends TrustedReplica[ToDoMapLattice](replicaId, crdt, aead) {

  private val uuidToDeltaGroupMap: mutable.Map[UUID, DecryptedDeltaGroup[ToDoMapLattice]] = mutable.Map.empty
  private var cleanupDeltaGroup: DecryptedDeltaGroup[ToDoMapLattice] = DecryptedDeltaGroup(Causal.bottom, CausalContext())

  override protected def disseminate(encryptedState: EncryptedDeltaGroup): Unit = {
    intermediary.receive(encryptedState)
  }

  def completeToDoItem(uuid: UUID): Unit = {
    val delta = crdt.putDelta(
      uuid,
      crdt.get(uuid).get.copy(completed = true)
    )
    localChangeOptimized(delta, uuid)
  }

  def addToDoItem(uuid: UUID, toDoEntry: ToDoEntry): Unit = {
    val delta = crdt.putDelta(uuid, toDoEntry)
    localChangeOptimized(delta, uuid)
  }

  def removeToDoItems(uuids: Seq[UUID]): Unit = {
    val delta = crdt.removeAllDelta(uuids)
    localChangeRemovalOptimized(delta, uuids)
  }

  def localChangeRemovalOptimized(delta: ToDoMapLattice, removedUuids: Seq[UUID]): Unit = {
    val eventDot = nextDot()
    dottedVersionVector.add(eventDot)

    // Merge all deltas referring to any of the removed uuids and remove old deltas (without replacement)
    val mergedOldDeltas = removedUuids.flatMap(uuidToDeltaGroupMap.remove).reduce((l, r) =>
      mergeDecryptedDeltas(l, r)
    )

    val newCleanupDelta = mergeDecryptedDeltas(
      mergeDecryptedDeltas(mergedOldDeltas, cleanupDeltaGroup),
      DecryptedDeltaGroup(delta, Set(eventDot))
    )
    cleanupDeltaGroup = newCleanupDelta

    disseminate(newCleanupDelta.encrypt(aead)(stateJsonCodec, dotSetJsonCodec))
  }

  def localChangeOptimized(delta: ToDoMapLattice, uuid: UUID): Unit = {
    val eventDot = nextDot()
    dottedVersionVector.add(eventDot)

    // Merge old delta referring to uuid
    val newDelta = uuidToDeltaGroupMap.get(uuid) match {
      case Some(oldUuidDeltaGroup) => DecryptedDeltaGroup(
        SemiLattice[ToDoMapLattice].merged(oldUuidDeltaGroup.deltaGroup, delta),
        oldUuidDeltaGroup.dottedVersionVector.add(eventDot)
      )

      case None => DecryptedDeltaGroup(delta, Set(eventDot))
    }
    uuidToDeltaGroupMap.put(uuid, newDelta)

    disseminate(newDelta.encrypt(aead)(stateJsonCodec, dotSetJsonCodec))
  }

  // call localChange instead of localChangeOptimized
  override def localChange(delta: ToDoMapLattice): Unit = ???
}

object ToDoListClient {
  type ToDoMapLattice = DeltaAddWinsLastWriterWinsMapLattice[UUID, ToDoEntry]

  private def mergeDecryptedDeltas(left: DecryptedDeltaGroup[ToDoMapLattice], right: DecryptedDeltaGroup[ToDoMapLattice]): DecryptedDeltaGroup[ToDoMapLattice] = {
    SemiLattice[DecryptedDeltaGroup[ToDoMapLattice]].merged(left, right)
  }
}
