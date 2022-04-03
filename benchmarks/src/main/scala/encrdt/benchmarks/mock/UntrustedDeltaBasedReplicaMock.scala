package de.ckuessner
package encrdt.benchmarks.mock

import encrdt.benchmarks.Codecs.deltaAwlwwmapJsonCodec
import encrdt.causality.CausalContext
import encrdt.crdts.DeltaAddWinsLastWriterWinsMap
import encrdt.encrypted.deltabased.{EncryptedDeltaGroup, UntrustedReplica}

import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import com.google.crypto.tink.Aead

import java.io.PrintWriter
import java.nio.file.{Files, Path}

class UntrustedDeltaBasedReplicaMock extends UntrustedReplica {
  override protected def prune(encryptedDeltaGroup: EncryptedDeltaGroup): Unit  = {}
  override protected def disseminate(encryptedState: EncryptedDeltaGroup): Unit = {}

  def getCausalContext: CausalContext = dottedVersionVector

  def size(): Long = {
    encryptedDeltaGroupStore.toList.map { delta =>
      delta.stateCiphertext.length + delta.serialDottedVersionVector.length
    }.sum
  }

  def decryptAndWriteRawDeltasToFile(aead: Aead, outFilepath: Path): Unit = {
    val os          = Files.newOutputStream(outFilepath)
    val printWriter = new PrintWriter(os)
    encryptedDeltaGroupStore.foreach(encDeltaGroup => {
      printWriter.print(new String(aead.decrypt(
        encDeltaGroup.stateCiphertext,
        encDeltaGroup.serialDottedVersionVector
      )))
      printWriter.print('|')
      printWriter.println(new String(encDeltaGroup.serialDottedVersionVector))
    })
    printWriter.close()
  }

  def decryptAndWriteDeltasToFile(aead: Aead, outFilePath: Path): Unit = {
    val os          = Files.newOutputStream(outFilePath)
    val printWriter = new PrintWriter(os)
    encryptedDeltaGroupStore.foreach(encDeltaGroup => printWriter.println(encDeltaGroup.decrypt(aead)))
    printWriter.close()
  }

  def decryptAndWriteStateToFile(aead: Aead, outFilePath: Path): Unit = {
    val os          = Files.newOutputStream(outFilePath)
    val printWriter = new PrintWriter(os)
    val crdt        = decrypt(aead)
    printWriter.write(writeToString(crdt.state))
    printWriter.close()
  }

  def decrypt(aead: Aead): DeltaAddWinsLastWriterWinsMap[String, String] = {
    val crdt = new DeltaAddWinsLastWriterWinsMap[String, String]("")
    encryptedDeltaGroupStore.map { encDeltaGroup =>
      encDeltaGroup.decrypt(aead)
    }.foreach { decDeltaGroup =>
      crdt.merge(decDeltaGroup.deltaGroup)
    }

    crdt
  }

  def copy(): UntrustedDeltaBasedReplicaMock = {
    val obj = new UntrustedDeltaBasedReplicaMock()
    obj.encryptedDeltaGroupStore = encryptedDeltaGroupStore
    obj.dottedVersionVector = dottedVersionVector
    obj
  }
}
