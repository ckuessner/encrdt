package de.ckuessner
package encrdt.benchmarks

import encrdt.benchmarks.mock.{DisseminationStats, ToDoListClient, ToDoListIntermediary}
import encrdt.benchmarks.todolist._
import encrdt.crdts.DeltaAddWinsLastWriterWinsMap

import better.files._
import com.google.crypto.tink.Aead

import java.util.UUID

object ToDoAppBenchmark extends App {

  val numInteractions  = 1_000_000
  val pruningThreshold = 50
  val keptToDos        = 20

  private val interactions: Iterable[ToDoListInteraction] =
    new ToDoListInteractionGenerator(pruningThreshold, keptToDos).generateInteractions(numInteractions)

  private val aead: Aead  = Helper.setupAead("AES128_GCM")
  val intermediaryReplica = new ToDoListIntermediary
  val clientCrdt          = new DeltaAddWinsLastWriterWinsMap[UUID, ToDoEntry]("client")
  val clientReplica       = new ToDoListClient("client", clientCrdt, aead, intermediaryReplica)

  val csvFileF = File("./benchmarks/results/todoapp_benchmark.csv")
  csvFileF.parent.createDirectories()
  val csvFile = csvFileF.newPrintWriter()
  csvFile.println(
    "interactions,intermediarySize,encDeltaCausalitySize,encDeltaCiphertextSize,intermediaryStoredDeltas,completedToDos,uncompletedToDos,last100InteractionsNanoTime,last100InteractionsDisseminatedBytes,last100InteractionsAdditionDisseminatedBytes,last100InteractionsCompletionDisseminatedBytes,last100InteractionsRemovalBytes"
  )

  val startNanoTime: Long                        = System.nanoTime()
  var lastCheckPointEndNanoTime: Long            = startNanoTime
  var lastDisseminationStats: DisseminationStats = DisseminationStats(0, 0, 0, 0)

  var counter = 0
  interactions.foreach { interaction =>
    performInteraction(interaction)
    counter += 1

    if (counter % 100 == 0) {
      val checkPointStartNanoTime        = System.nanoTime()
      val nanoTimeForLast100Interactions = checkPointStartNanoTime - lastCheckPointEndNanoTime
      val last100DisseminationStatDiff   = clientReplica.disseminationStats - lastDisseminationStats
      lastDisseminationStats = clientReplica.disseminationStats

      val storedDeltasOnIntermediary = intermediaryReplica.numberStoredDeltas
      val causalitySize              = intermediaryReplica.encDeltaCausalityInfoSizeInBytes()
      val deltaCipherTextSize        = intermediaryReplica.rawDeltaCiphertextSizeInBytes()

      val entries            = clientCrdt.values
      val completedEntries   = entries.filter(_._2.completed)
      val uncompletedEntries = entries.filterNot(_._2.completed)

      csvFile.println(
        s"$counter,${causalitySize + deltaCipherTextSize},$causalitySize,$deltaCipherTextSize,$storedDeltasOnIntermediary,${completedEntries.size},${uncompletedEntries.size},$nanoTimeForLast100Interactions,${last100DisseminationStatDiff.total},${last100DisseminationStatDiff.addition},${last100DisseminationStatDiff.completion},${last100DisseminationStatDiff.removal}"
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

  def performInteraction(interaction: ToDoListInteraction): Unit = interaction match {
    case AddToDoItem(uuid, toDoEntry) =>
      clientReplica.addToDoItem(uuid, toDoEntry)

    case CompleteToDoItem(uuid) =>
      clientReplica.completeToDoItem(uuid)

    case RemoveToDoItems(uuids) =>
      clientReplica.removeToDoItems(uuids)
  }
}
