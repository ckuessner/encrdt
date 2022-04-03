package de.ckuessner
package encrdt.benchmarks.mock

import encrdt.benchmarks.todolist.ToDoEntry

import java.util.UUID

trait ToDoListClient {
  def completeToDoItem(uuid: UUID): Unit
  def addToDoItem(uuid: UUID, toDoEntry: ToDoEntry): Unit
  def removeToDoItems(uuids: Seq[UUID]): Unit

  def disseminationStats: DisseminationStats
}
