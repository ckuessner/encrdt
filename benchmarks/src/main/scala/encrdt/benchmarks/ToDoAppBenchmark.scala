package de.ckuessner
package encrdt.benchmarks

import encrdt.benchmarks.mock.{ToDoListClient, ToDoListIntermediary}
import encrdt.benchmarks.todolist._
import encrdt.crdts.DeltaAddWinsLastWriterWinsMap

import com.google.crypto.tink.Aead

import better.files._

import java.io.PrintWriter
import java.nio.file.{Files, Paths}
import java.util.UUID

object ToDoAppBenchmark extends App {
  private val aead: Aead = Helper.setupAead("AES128_GCM")

  val intermediaryReplica = new ToDoListIntermediary

  val clientCrdt = new DeltaAddWinsLastWriterWinsMap[UUID, ToDoEntry]("client")
  val clientReplica = new ToDoListClient("client", clientCrdt, aead, intermediaryReplica)

  val numInteractions = 100_000
  private val interactions: Iterable[ToDoListInteraction] = ToDoListInteractionGenerator.generateInteractions(numInteractions)

  var counter = 0
  val startTime: Long = System.currentTimeMillis()
  var lastCheckPointTime: Long = startTime

  val csvFileF = File("./benchmarks/results/todoapp_benchmark.csv")
  csvFileF.parent.createDirectories()

  val csvFile = csvFileF.newPrintWriter()

  csvFile.println("interactions,intermediarySize,encDeltaCausalitySize,encDeltaCiphertextSize,intermediaryStoredDeltas,completedToDos,uncompletedToDos")

  interactions.foreach { interaction =>
    performInteraction(interaction)
    counter += 1

    if (counter % 100 == 0) {
      val storedDeltasOnIntermediary = intermediaryReplica.numberStoredDeltas
      val causalitySize = intermediaryReplica.encDeltaCausalityInfoSizeInBytes()
      val deltaCipherTextSize = intermediaryReplica.rawDeltaCiphertextSizeInBytes()

      val entries = clientCrdt.values
      val completedEntries = entries.filter(_._2.completed)
      val uncompletedEntries = entries.filterNot(_._2.completed)

      csvFile.println(s"$counter,${causalitySize + deltaCipherTextSize},$causalitySize,$deltaCipherTextSize,$storedDeltasOnIntermediary,${completedEntries.size},${uncompletedEntries.size}")
    }

    if (counter % 1000 == 0) {
      println(s"$counter interactions completed / Last 1000 interactions took ${System.currentTimeMillis() - lastCheckPointTime}ms")
      lastCheckPointTime = System.currentTimeMillis()
    }
  }

  println(s"Took ${System.currentTimeMillis()-startTime}ms overall")

  csvFile.close()

  def performInteraction(interaction: ToDoListInteraction): Unit = interaction match {
    case AddToDoItem(uuid, toDoEntry) =>
      clientReplica.addToDoItem(uuid, toDoEntry)

    case CompleteToDoItem(uuid) =>
      clientReplica.completeToDoItem(uuid)

    case RemoveToDoItems(uuids) =>
      clientReplica.removeToDoItems(uuids)
  }
}
