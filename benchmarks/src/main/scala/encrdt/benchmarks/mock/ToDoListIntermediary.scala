package de.ckuessner
package encrdt.benchmarks.mock

import encrdt.encrypted.deltabased.{DeltaPruning, EncryptedDeltaGroup, UntrustedReplica}

class ToDoListIntermediary extends UntrustedReplica with DeltaPruning with IntermediarySizeInfo {
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
