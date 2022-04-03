package de.ckuessner
package encrdt.benchmarks.mock.insecure

import encrdt.benchmarks.mock.SecureToDoListClient.ToDoMapLattice
import encrdt.benchmarks.mock.{SecureToDoListClient, ToDoListIntermediary}
import encrdt.benchmarks.todolist.ToDoEntry
import encrdt.crdts.DeltaAddWinsLastWriterWinsMap
import encrdt.encrypted.deltabased.{DecryptedDeltaGroup, EncryptedDeltaGroup}

import com.github.plokhotnyuk.jsoniter_scala.core.writeToArray

import java.util.UUID

class InsecureToDoListClient(
    replicaId: String,
    crdt: DeltaAddWinsLastWriterWinsMap[UUID, ToDoEntry],
    untrustedReplica: ToDoListIntermediary
) extends SecureToDoListClient(replicaId, crdt, null, untrustedReplica) {
  override protected def encryptAndDisseminate(newDeltaGroup: DecryptedDeltaGroup[ToDoMapLattice]): Unit = {
    // Serialize but don't encrypt!
    val serialPlaintextDeltaGroup = writeToArray(newDeltaGroup.deltaGroup)
    val serialDottedVersionVector = writeToArray(newDeltaGroup.dottedVersionVector)

    disseminate(
      EncryptedDeltaGroup(serialPlaintextDeltaGroup, serialDottedVersionVector)
    )
  }
}
