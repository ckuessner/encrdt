package de.ckuessner
package encrdt.benchmarks

import encrdt.benchmarks.Codecs.toDoMapCodec
import encrdt.benchmarks.mock._
import encrdt.benchmarks.mock.insecure.{AlternativeInsecureToDoListClient, AlternativeInsecureToDoListIntermediary}
import encrdt.benchmarks.todolist._
import encrdt.crdts.DeltaAddWinsLastWriterWinsMap

import better.files._
import com.google.crypto.tink.Aead
import encrdt.encrypted.deltabased.{DeltaPruning, NoPruning}

import java.util.UUID

object ToDoAppBenchmark extends App {
  println("Running ToDoAppBenchmark with encryption=true, pruning=true")
  new ToDoAppBenchmark(true, true).runBenchmark()
  println("Running ToDoAppBenchmark with encryption=true, pruning=false")
  new ToDoAppBenchmark(true, false).runBenchmark()
  println("Running ToDoAppBenchmark with encryption=false")
  new ToDoAppBenchmark(true, false).runBenchmark()
}

class ToDoAppBenchmark(
    val USE_ENCRYPTION: Boolean,
    val USE_PRUNING: Boolean,
    val ENCRYPTION_ALGO: String = "XCHACHA20_POLY1305"
) {

  val numInteractions  = 1_000_000
  val pruningThreshold = 50
  val keptToDos        = 20

  private val interactions: Iterable[ToDoListInteraction] =
    new ToDoListInteractionGenerator(pruningThreshold, keptToDos).generateInteractions(numInteractions)

  private val clientCrdt = new DeltaAddWinsLastWriterWinsMap[UUID, ToDoEntry]("client")

  private var intermediarySizeInfo: IntermediarySizeInfo = _
  private var aead: Aead                                 = _
  private var clientReplica: ToDoListClient              = _

  if (USE_ENCRYPTION) {
    aead = Helper.setupAead(ENCRYPTION_ALGO)
    val intermediaryReplica =
      if (USE_PRUNING) new ToDoListIntermediary with DeltaPruning
      else new ToDoListIntermediary with NoPruning
    intermediarySizeInfo = intermediaryReplica
    clientReplica = new SecureToDoListClient("client", clientCrdt, aead, intermediaryReplica)
  } else {
    val intermediaryReplica = new AlternativeInsecureToDoListIntermediary("intermediary")
    intermediarySizeInfo = intermediaryReplica
    clientReplica = new AlternativeInsecureToDoListClient("client", clientCrdt, intermediaryReplica)
  }

  val csvFileF = {
    val baseFilename = "./benchmarks/results/todoapp_benchmark"
    if (USE_ENCRYPTION) {
      if (USE_PRUNING) File(baseFilename + ".csv")
      else File(baseFilename + "_no_pruning.csv")
    } else File(baseFilename + " trusted intermediary.csv")
  }
  csvFileF.parent.createDirectories()
  val csvFile = csvFileF.newPrintWriter()
  csvFile.println(
    "interactions,intermediarySize,encDeltaCausalitySize,encDeltaCiphertextSize,intermediaryStoredDeltas,completedToDos,uncompletedToDos,last100InteractionsNanoTime,last100InteractionsDisseminatedBytes,last100InteractionsAdditionDisseminatedBytes,last100InteractionsCompletionDisseminatedBytes,last100InteractionsRemovalBytes"
  )

  var lastDisseminationStats: DisseminationStats = DisseminationStats(0, 0, 0, 0)

  val startNanoTime: Long             = System.nanoTime()
  var lastCheckPointEndNanoTime: Long = startNanoTime

  var counter = 0

  def runBenchmark(): Unit = {
    interactions.foreach { interaction =>
      performInteraction(interaction)
      counter += 1

      if (counter % 100 == 0) {
        val checkPointStartNanoTime = System.nanoTime()
        val nanoTimeForLast100Interactions = checkPointStartNanoTime - lastCheckPointEndNanoTime
        val last100DisseminationStatDiff = clientReplica.disseminationStats - lastDisseminationStats
        lastDisseminationStats = clientReplica.disseminationStats

        val storedDeltasOnIntermediary = intermediarySizeInfo.numberStoredDeltas
        val causalitySize = intermediarySizeInfo.encDeltaCausalityInfoSizeInBytes
        val rawDeltaSize = intermediarySizeInfo.rawDeltasSizeInBytes

        val entries = clientCrdt.values
        val completedEntries = entries.filter(_._2.completed)
        val uncompletedEntries = entries.filterNot(_._2.completed)

        csvFile.println(
          s"$counter,${causalitySize + rawDeltaSize},$causalitySize,$rawDeltaSize,$storedDeltasOnIntermediary,${completedEntries.size},${uncompletedEntries.size},$nanoTimeForLast100Interactions,${last100DisseminationStatDiff.total},${last100DisseminationStatDiff.addition},${last100DisseminationStatDiff.completion},${last100DisseminationStatDiff.removal}"
        )

        if (counter % 1_000 == 0) {
          println(
            s"$counter/$numInteractions interactions completed / avg over last 100: ${(checkPointStartNanoTime - lastCheckPointEndNanoTime) / (1_000_000.0 * 100)}ms"
          )
        }

        lastCheckPointEndNanoTime = System.nanoTime()
      }
    }

    csvFile.close()

    println("Overall " + clientReplica.disseminationStats)
  }

  def performInteraction(interaction: ToDoListInteraction): Unit = interaction match {
    case AddToDoItem(uuid, toDoEntry) =>
      clientReplica.addToDoItem(uuid, toDoEntry)

    case CompleteToDoItem(uuid) =>
      clientReplica.completeToDoItem(uuid)

    case RemoveToDoItems(uuids) =>
      clientReplica.removeToDoItems(uuids)
  }
}
