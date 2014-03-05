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
import akka.pattern.ask
import akka.actor.Status
import scala.concurrent.Future
import org.slf4j.Logger
import scala.concurrent.Promise
import akka.util.Timeout
import scala.concurrent.ExecutionContext
import org.slf4j.LoggerFactory


sealed trait Domain
case class TodoDto(id: Option[String], title: String, completed: Boolean) extends Domain
object TodoDto {
  implicit def todoToDto(todo: Todo): TodoDto =
    TodoDto(Some(todo.id.toString),todo.content.title,todo.content.completed)
  implicit def dtoToTodo(todoDto: TodoDto): Todo = {
    val id = todoDto.id match {
      case None => TodoId.generate
      case Some(ident) => 
        ident match {
          case TodoId(todoId) => todoId
          case _ => TodoId.generate 
        }
    }
    val content = TodoContent(todoDto.title,todoDto.completed)
    Todo(id,content)
  }
  def unapply(todo: Todo): Option[TodoDto] =
    Some(TodoDto(Some(todo.id.toString),todo.content.title,todo.content.completed))
}

object TodoRepository {
  sealed trait QueryMessage
  case class ShowMessage(id: String) extends QueryMessage
  case object ListMessage extends QueryMessage
  
  sealed trait QueryResponse
  case class ShowResponse(todo: Option[TodoDto]) extends QueryResponse
  case class ListResponse(todos: Seq[TodoDto]) extends QueryResponse

  sealed trait CommandMessage
  case class UpdateMessage(todo: TodoDto) extends CommandMessage
  case class CreateMessage(todo: TodoDto) extends CommandMessage
  case class DeleteMessage(id: String) extends CommandMessage
  
  sealed trait CommandResponse
  case class Updated(updated: Boolean) extends CommandResponse
  case class Created(todoOrId: Either[String, TodoDto]) extends CommandResponse
  
  def uuid =
    UUID.randomUUID().toString().replaceAll("-","")
}

trait TodoRepository {

  implicit val timeout: Timeout
  val processor: ActorRef
  val log: Logger
  
  def withErrorHandling[A](errLog: String)(op: => Future[A]): Future[A] = {
    try {
      op
    } catch {
      case NonFatal(e) =>
        log.error(s"Error while procesing $errLog request",e)
        Promise[A]().failure(e).future
    }
  }
}

trait TodoRepositoryQuery extends TodoRepository {
  import TodoRepositoryProcessor._
  import TodoRepository._
  import TodoId._
  import TodoDto._
  
  def show(id: String)(implicit ec: ExecutionContext): Future[ShowResponse] = withErrorHandling("show") {
      log.debug("Received show request for id {}",id)
      TodoId.fromString(id) match {
        case Some(todoId) => 
          log.debug(s"Matched uuid $todoId, forwarding Get")
          val result = (processor ? Get(todoId)).mapTo[GetResponse]
          result.map { r => ShowResponse(r.todo.map(todoToDto)) }
        case None =>
          log.debug(s"Couldn't match id $id, responding with no matches")
          Promise[ShowResponse]().success(ShowResponse(None)).future
      }
	} 

  def list(pageSize: Int)(implicit ec: ExecutionContext): Future[ListResponse] = 
    withErrorHandling("list") {
    	val result = (processor ? MostRecent(pageSize)).mapTo[MostRecentResponse]
    	result.map { r => ListResponse(r.todos.map(todoToDto) ) }
    } 
}

trait TodoRepositoryCommand extends TodoRepository {
 
  import TodoId._
  import TodoDto._
  import TodoRepository._
  import TodoRepositoryProcessor._
  
  def update(updated: TodoDto)(implicit ec: ExecutionContext): Future[Updated] = withErrorHandling("update") {
      val result = (processor ? Update(updated)).mapTo[UpdateResponse]
      result.map(ur => Updated(ur.updated))
    }

  def create(created: TodoDto)(implicit ec: ExecutionContext): Future[Created] = withErrorHandling("create") {
      val result = (processor ? Create(created)).mapTo[CreateResponse]
      result.map(cr => cr.todoOrId match {
        case Left(todoId) => Created(Left(todoId.uuid.toString))
        case Right(todo) => Created(Right(todoToDto(todo)))
      })
	} 

  def delete(id: String)(implicit ec: ExecutionContext): Future[Updated] = withErrorHandling("delete") {
      fromString(id) match {
        case None => Promise[Updated]().success(Updated(false)).future
        case Some(todoId) =>
          val result = (processor ? Delete(todoId)).mapTo[UpdateResponse]
          result.map(ur => Updated(ur.updated))
      }
	} 
}

class TodoRepositoryImpl(val processor: ActorRef, to: Timeout) extends TodoRepositoryQuery with TodoRepositoryCommand {

  implicit val timeout: Timeout = to
  val log = LoggerFactory.getLogger(getClass)

}