package de.ckuessner
package encrdt.benchmarks.todolist

import com.github.javafaker.Faker

import java.time.LocalDateTime
import scala.util.Random

class ToDoEntryGenerator(private val random: Random = new Random(42)) {
  private val textGenerator = new Faker(new java.util.Random(random.nextLong())).lorem()

  def nextTodoEntry(textLengthMinInclusive: Int, textLengthMaxExclusive: Int): ToDoEntry = {
    val textLength = random.between(textLengthMinInclusive, textLengthMaxExclusive)
    textGenerator.characters(textLength)
    val text = textGenerator.characters(textLength)
    ToDoEntry(text, completed = false, LocalDateTime.now())
  }

  def nextTodoEntries(numberEntries: Int, textLengthMinInclusive: Int, textLengthMaxExclusive: Int): Array[ToDoEntry] = {
    val generatedToDoEntries = new Array[ToDoEntry](numberEntries)

    0 until numberEntries foreach {
      generatedToDoEntries(_) = nextTodoEntry(textLengthMinInclusive, textLengthMaxExclusive)
    }

    return generatedToDoEntries
  }
}
