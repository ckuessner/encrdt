package de.ckuessner
package encrdt.benchmarks.mock

import encrdt.encrypted.deltabased.{EncryptedDeltaGroup, UntrustedReplica}

abstract class ToDoListIntermediary extends UntrustedReplica with IntermediarySizeInfo {
  def sizeInBytes: Long = {
    encryptedDeltaGroupStore.iterator.map { encDelta =>
      encDelta.serialDottedVersionVector.length + encDelta.stateCiphertext.length
    }.sum
  }

  def encDeltaCausalityInfoSizeInBytes: Long = {
    encryptedDeltaGroupStore.iterator.map(_.serialDottedVersionVector.length).sum
  }

  def rawDeltasSizeInBytes: Long = {
    encryptedDeltaGroupStore.iterator.map(_.stateCiphertext.length).sum
  }

  def numberStoredDeltas: Int = encryptedDeltaGroupStore.size

  override protected def disseminate(encryptedState: EncryptedDeltaGroup): Unit = {}
}
