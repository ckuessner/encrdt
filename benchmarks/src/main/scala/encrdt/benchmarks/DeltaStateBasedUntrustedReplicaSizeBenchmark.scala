package de.ckuessner
package encrdt.benchmarks

import encrdt.benchmarks.Codecs.{causalContextCodec, deltaAwlwwmapJsonCodec}
import encrdt.benchmarks.mock.UntrustedDeltaBasedReplicaMock
import encrdt.causality.LamportClock
import encrdt.causality.impl.ArrayCausalContext
import encrdt.crdts.DeltaAddWinsLastWriterWinsMap
import encrdt.encrypted.deltabased.DecryptedDeltaGroup

import com.github.plokhotnyuk.jsoniter_scala.core.writeToArray
import com.google.crypto.tink.Aead

import java.io.PrintWriter
import java.nio.file.{Files, Path, Paths}

object DeltaStateBasedUntrustedReplicaSizeBenchmark extends App with DeltaStateUntrustedReplicaSizeBenchEnvironment {
  val csvFile = new PrintWriter(Files.newOutputStream(Paths.get("./benchmarks/results/delta_state_size_benchmark.csv")))
  println(csvHeader)
  csvFile.println(csvHeader)

  val minElementExponent = 1 // 10 ** this as minimum tested total elements added to CRDT
  val maxElementExponent = 3 // 10 ** this as maximum tested total elements added to CRDT
  for (totalElements <- (minElementExponent to maxElementExponent).map(i => math.pow(10, i).toInt)) {
    // val totalElements     = 10_000 // Commment out, if using the loop above
    val maxParallelStates = 4
    val elementsInCommon  = totalElements - maxParallelStates

    val benchmarkSharedUntrustedReplica = new UntrustedDeltaBasedReplicaMock;
    var benchmarkSharedCurrentDot       = LamportClock(0, "0");
    val benchmarkSharedCrdt: DeltaAddWinsLastWriterWinsMap[String, String] =
      new DeltaAddWinsLastWriterWinsMap[String, String]("0")

    for (i <- 0 until elementsInCommon) {
      val entry = dummyKeyValuePairs(i)
      benchmarkSharedCurrentDot = benchmarkSharedCurrentDot.advance("0")
      val delta    = benchmarkSharedCrdt.putDelta(entry._1, entry._2)
      val encDelta = DecryptedDeltaGroup(delta, ArrayCausalContext.single(benchmarkSharedCurrentDot)).encrypt(aead)
      benchmarkSharedUntrustedReplica.receive(encDelta)
    }

    for (parallelStates <- 1 to maxParallelStates) {
      val crdt =
        new DeltaAddWinsLastWriterWinsMap[String, String]("0", benchmarkSharedCrdt.state, benchmarkSharedCrdt.deltas)
      val untrustedReplica = benchmarkSharedUntrustedReplica.copy()
      var localDot         = LamportClock(0, "0");
      // Populate CRDT with missing elements (before adding concurrent updates)
      {
        for (i <- elementsInCommon until (totalElements - parallelStates)) {
          val entry = dummyKeyValuePairs(i)
          localDot = localDot.advance("0")
          val delta    = crdt.putDelta(entry._1, entry._2)
          val encDelta = DecryptedDeltaGroup(delta, ArrayCausalContext.single(localDot)).encrypt(aead)
          untrustedReplica.receive(encDelta)
        }

        var unmergedDeltas = List.empty[DeltaAddWinsLastWriterWinsMap.StateType[String, String]]
        for (replicaId <- 1 to parallelStates) {
          val entry = dummyKeyValuePairs(totalElements - replicaId)
          val replicaSpecificCrdt =
            new DeltaAddWinsLastWriterWinsMap[String, String](replicaId.toString, crdt.state, crdt.deltas)
          val delta = replicaSpecificCrdt.putDelta(entry._1, entry._2)
          unmergedDeltas = unmergedDeltas :+ delta
          val dot      = LamportClock(1, replicaId.toString)
          val encState = DecryptedDeltaGroup(delta, ArrayCausalContext.single(dot)).encrypt(aead)
          untrustedReplica.receive(encState)
        }

        val mergedCrdt = new DeltaAddWinsLastWriterWinsMap[String, String]("0", crdt.state, crdt.deltas)
        unmergedDeltas.foreach(delta => mergedCrdt.merge(delta))
        val serializedDecryptedMergedState = writeToArray(mergedCrdt.state)

        val mergedSize      = serializedDecryptedMergedState.length
        val mergedEncrypted = DecryptedDeltaGroup(mergedCrdt.state, untrustedReplica.getCausalContext).encrypt(aead)
        val mergedEncryptedSize =
          mergedEncrypted.serialDottedVersionVector.length + mergedEncrypted.stateCiphertext.length
        val csvLine =
          s"$parallelStates,${totalElements - parallelStates},$totalElements,${untrustedReplica.size()},$mergedSize,$mergedEncryptedSize"
        println(csvLine)
        csvFile.println(csvLine)
      }
    }
  }

  csvFile.close()
}

object DeltaStateBasedUntrustedReplicaSizeBenchmarkLinearScaling extends App
    with DeltaStateUntrustedReplicaSizeBenchEnvironment {
  val csvFile = new PrintWriter(
    Files.newOutputStream(Paths.get("./benchmarks/results/delta_state_size_benchmark_linear_sampling.csv"))
  )
  println(csvHeader)
  csvFile.println(csvHeader)
  val crdt: DeltaAddWinsLastWriterWinsMap[String, String] = new DeltaAddWinsLastWriterWinsMap[String, String]("0")
  var currentDot                                          = LamportClock(0, "0")

  val untrustedReplica = new UntrustedDeltaBasedReplicaMock()

  val totalElements = 10_000
  for (i <- 0 until totalElements) {
    val entry = dummyKeyValuePairs(i)
    val delta = crdt.putDelta(entry._1, entry._2)
    currentDot = currentDot.advance("0")
    val encDelta = DecryptedDeltaGroup(delta, ArrayCausalContext.single(currentDot)).encrypt(aead)
    untrustedReplica.receive(encDelta)

    if ((i + 1) % 1_000 == 0) {
      val serializedInternalCrdt = writeToArray(crdt.state)
      val encrdtFullyMerged      = DecryptedDeltaGroup(crdt.state, untrustedReplica.getCausalContext).encrypt(aead)
      val encrdtFullyMergedSize =
        encrdtFullyMerged.stateCiphertext.length + encrdtFullyMerged.serialDottedVersionVector.length

      val csvLine =
        s"1,${i + 1},${i + 1},${untrustedReplica.size()},${serializedInternalCrdt.length},$encrdtFullyMergedSize"
      println(csvLine)
      csvFile.println(csvLine)
    }
  }

  csvFile.close()
}

trait DeltaStateUntrustedReplicaSizeBenchEnvironment {
  val csvHeader = "concurrentUpdates,commonElements,uniqueElements,untrustedReplicaSize,mergedSize,mergedEncryptedSize"

  val outDir: Path = Paths.get("./", "benchmarks", "results")
  if (!outDir.toFile.exists()) outDir.toFile.mkdirs()
  val aead: Aead                                  = Helper.setupAead("AES128_GCM")
  val dummyKeyValuePairs: Array[(String, String)] = Helper.dummyKeyValuePairs(10_000)
}
