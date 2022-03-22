package de.ckuessner
package encrdt.benchmarks.mock

import encrdt.encrypted.deltabased.{DeltaPruning, EncryptedDeltaGroup, UntrustedReplica}

class ToDoListIntermediary extends UntrustedReplica with DeltaPruning {
  def sizeInBytes(): Long = {
    var size = 0
    // Don't replace with map, convert to datatype other than Set first!
    encryptedDeltaGroupStore.foreach { encDelta =>
      size += encDelta.serialDottedVersionVector.length + encDelta.stateCiphertext.length
    }
    size
  }

  def encDeltaCausalityInfoSizeInBytes(): Long = {
    var size = 0
    // Don't replace with map, convert to datatype other than Set first!
    encryptedDeltaGroupStore.foreach { encDelta =>
      size += encDelta.serialDottedVersionVector.length
    }
    size
  }

  def rawDeltaCiphertextSizeInBytes(): Long = {
    var size = 0
    // Don't replace with map, convert to datatype other than Set first!
    encryptedDeltaGroupStore.foreach { encDelta =>
      size += encDelta.stateCiphertext.length
    }
    size
  }

  def numberStoredDeltas: Int = encryptedDeltaGroupStore.size


  override protected def disseminate(encryptedState: EncryptedDeltaGroup): Unit = {}
}
