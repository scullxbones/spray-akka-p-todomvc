package net.bs.models

import akka.persistence.EventsourcedProcessor
import akka.actor.ActorLogging
import akka.persistence.SnapshotOffer
import akka.persistence.SnapshotSelectionCriteria
import akka.actor.PoisonPill

object TodoRepositoryProcessor {
  
  sealed trait CommandMessage
  case class Create(newTodo: Todo) extends CommandMessage
  case class Update(newTodo: Todo) extends CommandMessage
  case object Delete extends CommandMessage
  case object Get extends CommandMessage
  
  sealed trait EventMessage
  case class New(todo: Todo) extends EventMessage
  case class NameChange(newName: String) extends EventMessage
  case class CompletionChange(newStatus: Boolean) extends EventMessage
  
}

class TodoRepositoryProcessor(todoId: String) extends EventsourcedProcessor with ActorLogging {
  import TodoRepositoryProcessor._
  
  private[this] var maybeTodo = Option.empty[Todo]
  
  override def processorId = s"todo-processor-$todoId"
  
  override val receiveCommand: Receive = {
    case command: CommandMessage => receiveCommand(command)
  }
  
  private[this] def receiveCommand(command: CommandMessage) = command match {
    case Update(updated) => 
      maybeTodo.map { todo =>
        if (updated.completed != todo.completed) persist(CompletionChange(updated.completed))(receiveEvent)
        if (updated.title != todo.title && !updated.title.isEmpty) persist(NameChange(updated.title))(receiveEvent)
      }
    case Delete =>
      deleteMessages(lastSequenceNr, false)
      deleteSnapshots(new SnapshotSelectionCriteria)
      self ! PoisonPill
    case Create(newTodo) =>
      if (maybeTodo.isEmpty) persist(New(newTodo))(receiveEvent)
    case Get =>
      maybeTodo.map { sender ! _ }
  }

  override val receiveRecover: Receive = {
    case event: EventMessage => persist(event)(receiveEvent)
    case SnapshotOffer(_, snapshot: Todo) => maybeTodo = Some(snapshot)
  }
  
  private[this] def receiveEvent(event: EventMessage) = event match {
    case NameChange(name) => maybeTodo = maybeTodo.map(_.copy(title = name))
    case CompletionChange(completed) => maybeTodo = maybeTodo.map(_.copy(completed = completed))
    case New(todo) => maybeTodo = Some(todo)
  }
}
