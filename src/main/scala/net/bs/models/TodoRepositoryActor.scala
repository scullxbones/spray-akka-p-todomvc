package net.bs.models

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.persistence.EventsourcedProcessor
import akka.persistence.Persistent
import java.util.UUID
import scala.language.implicitConversions
import scala.util.Try
import scala.util.control.NonFatal
import akka.persistence.SnapshotOffer
import akka.actor.ActorRef

sealed trait Domain
case class Todo(id: Option[String], title: String, completed: Boolean) extends Domain

object TodoRepositoryActor {
  sealed trait QueryMessage
  case class ShowMessage(id: String) extends QueryMessage
  case object ListMessage extends QueryMessage
  
  sealed trait QueryResponse
  case class ShowResponse(todo: Option[Todo]) extends QueryResponse
  case class ListResponse(todos: List[Todo]) extends QueryResponse

  sealed trait CommandMessage
  case class UpdateMessage(todo: Todo) extends CommandMessage
  case class CreateMessage(todo: Todo) extends CommandMessage
  case class DeleteMessage(id: String) extends CommandMessage
  
  sealed trait CommandResponse
  case class UpdateResponse(updated: Boolean) extends CommandResponse
  case class CreateResponse(todoOrId: Either[String, Todo]) extends CommandResponse
  
  sealed trait EventMessage
  case class UpdateTodo(todo: Todo) extends EventMessage
  case class CreateTodo(todo: Todo) extends EventMessage
  case class DeleteTodo(todo: Todo) extends EventMessage
  
  def uuid =
    UUID.randomUUID().toString().replaceAll("-","")
}

trait TodoRepositoryQuery { self: Actor with ActorLogging =>
  import TodoRepositoryActor._
  
  def show(id: String, store: TodoStore): Unit = {
    try {
      log.debug("Received show request for id {}",id)
	    val result = store.get(id)
	    sender ! ShowResponse(result)
	} 
    catch {
	  case e: Exception ⇒
      	log.error(e,"Error while processing show request")
	    sender ! akka.actor.Status.Failure(e)
	    throw e
	}
  }

  def list(store: TodoStore): Unit = {
    try {
      val result = store.values
      sender ! ListResponse(result.toList)
	} 
    catch {
	  case e: Exception ⇒
	    sender ! akka.actor.Status.Failure(e)
	    throw e
	}
  }
}

trait TodoRepositoryCommand { me: Actor with ActorLogging =>
 
  import TodoRepositoryActor._
  import TodoRepositoryProcessor._
  
  val store: TodoProcessorStore
  
  private[this] def validateTitle(todo: Todo) =
    if (todo.title.length > 0)
      Some(todo.title)
    else
      None
  
  /*
   * Valid update if
   * - id exists
   * - todo corresponding to id exists
   * - title is not empty
   */
  def update(updated: Todo): Unit =
    try {
      val ref = for {
        id <- updated.id
        found <- store.get(id)
      } yield found
      ref match {
        case Some(proc) => {
          sender ! UpdateResponse(true)
          self ! UpdateTodo(updated)
        }
        case None => {
          sender ! UpdateResponse(false)
        }
      }
    } catch {
      case NonFatal(e) ⇒
        sender ! akka.actor.Status.Failure(e)
    }

  /*
   * Valid create if
   * - todo with id does not already exist
   * - title is not empty
   */
  def create(created: Todo): Unit = {
    try {
      val matching = for {
        id <- created.id
        found <- store.get(id)
      } yield found
      
      matching match {
        case None => {
          val newId = uuid
          self ! CreateTodo(created.copy(id = Some(newId)))
          sender ! CreateResponse(Left(newId))
        }
        case Some(todo) => {
          sender ! CreateResponse(Right(todo))
        }
      }
	} 
    catch {
	  case NonFatal(e) ⇒
	    sender ! akka.actor.Status.Failure(e)
	}
  }

  def delete(id: String): Unit = {
    try {
      store.get(id) match {
        case Some(ref) => {
          ref ! Delete
          sender ! UpdateResponse(true)
        }
        case None => {
          sender ! UpdateResponse(false)
        }
      }
	} 
    catch {
	  case NonFatal(e) ⇒
	    sender ! akka.actor.Status.Failure(e)
	}
  }
}

trait TodoProcessorStore {
  def get(id: String): Option[ActorRef]
}

case class TodoProcessors(contents: Map[String,ActorRef]) extends TodoProcessorStore 

class TodoRepositoryActor extends Actor with ActorLogging 
	with TodoRepositoryQuery with TodoRepositoryCommand {
  
  import TodoRepositoryActor._


}