package net.bs.models

import akka.persistence.EventsourcedProcessor
import akka.actor.ActorLogging
import akka.persistence.SnapshotOffer
import akka.persistence.SnapshotSelectionCriteria
import akka.actor.PoisonPill
import java.util.UUID
import scala.util.control.Exception._
import scala.language.implicitConversions
import spray.json.DefaultJsonProtocol
import spray.json.RootJsonFormat
import spray.json.JsObject
import spray.json.JsString
import spray.json.JsValue
import spray.json.DeserializationException

object TodoJsonProtocol extends DefaultJsonProtocol {
  
  implicit object TodoIdFormat extends RootJsonFormat[TodoId] {
    def write(c: TodoId) = JsObject("id" -> JsString(c.uuid.toString))
    def read(value: JsValue) = value.asJsObject.getFields("id") match {
      case Seq(JsString(id)) => TodoId.fromString(id).getOrElse(throw new DeserializationException(s"Invalid id: $id"))
      case _ => throw new DeserializationException(s"Not a TodoId: ${value.prettyPrint}")
    }
  } 
  
  implicit val todoContentFormat = jsonFormat2(TodoContent.apply)
  implicit val todoFormat = jsonFormat2(Todo.apply)
}

case class Todo(id: TodoId, content: TodoContent)
case class TodoContent(title: String, completed: Boolean)
case class TodoId(uuid: UUID)
object TodoId {
  def generate: TodoId = TodoId(UUID.randomUUID())
  
  implicit def todoId2String(todoId: TodoId): String = todoId.uuid.toString

  def fromString(s: String): Option[TodoId] = s match {
    case TodoIdRegex(uuid) => catching(classOf[RuntimeException]) opt { TodoId(UUID.fromString(uuid)) }
    case _ => None
  }
  
  def unapply(s: String): Option[TodoId] = fromString(s)

  private val TodoIdRegex = """([a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12})""".r
}

object TodoRepositoryProcessor {
  
  object EventProtocol extends DefaultJsonProtocol {
    import TodoJsonProtocol._
    
    implicit val todoAddedFormat = jsonFormat2(TodoAdded.apply)
    implicit val todoChangedFormat = jsonFormat2(TodoChanged.apply)
    implicit val todoDeletedFormat = jsonFormat1(TodoDeleted.apply)
    
    implicit val processorStateFormat = jsonFormat2(ProcessorState.apply)
  }
  
  sealed trait CommandMessage
  case class Create(newTodo: Todo) extends CommandMessage
  case class Update(newTodo: Todo) extends CommandMessage
  case class Delete(id: TodoId) extends CommandMessage
  case class Get(id: TodoId) extends CommandMessage
  case class MostRecent(count: Int) extends CommandMessage

  sealed trait CommandResponse
  case class GetResponse(todo: Option[Todo]) extends CommandResponse
  case class MostRecentResponse(todos: Seq[Todo]) extends CommandResponse   
  case class UpdateResponse(updated: Boolean) extends CommandResponse
  case class CreateResponse(todoOrId: Either[TodoId, Todo]) extends CommandResponse
  
  sealed trait EventMessage
  case class TodoAdded(id: TodoId, content: TodoContent) extends EventMessage
  case class TodoChanged(id: TodoId, content: TodoContent) extends EventMessage
  case class TodoDeleted(id: TodoId) extends EventMessage

  case class ProcessorState(byId: Map[TodoId, Todo] = Map.empty, orderedByTimeAdded: Seq[TodoId] = Seq.empty) {
	  def get(id: TodoId): Option[Todo] = byId.get(id)
	  def mostRecent(n: Int): Seq[Todo] = orderedByTimeAdded.takeRight(n).reverse.map(byId)
	 
	  def apply(event: EventMessage): ProcessorState = event match {
	    case TodoAdded(id, content) =>
	      this.copy(byId = byId.updated(id, Todo(id, content)), orderedByTimeAdded = orderedByTimeAdded :+ id)
	    case TodoChanged(id, content) =>
	      this.copy(byId = byId.updated(id, byId(id).copy(content = content)))
	    case TodoDeleted(id) =>
	      this.copy(byId = byId - id, orderedByTimeAdded = orderedByTimeAdded.filterNot(_ == id))
	  }
  }
  
  object ProcessorState {
    def fromHistory(events: EventMessage*): ProcessorState = events.foldLeft(ProcessorState())(_ apply _)
  }
}

class TodoRepositoryProcessor(tenant: String = "DEFAULT", commandsPerSnap:Int = 100) extends EventsourcedProcessor with ActorLogging {
  import TodoRepositoryProcessor._
  
  private[this] var state = ProcessorState()
  
  override def processorId = s"todo_processor_$tenant"
  
  override val receiveCommand: Receive = {
    case command: CommandMessage => 
      receiveCommand(command)
      context.become(receiveCommandDecrement(commandsPerSnap-1))
  }
  
  private[this] def receiveCommandDecrement(remainingUntilSnapshot: Int): Receive = {
    case command: CommandMessage =>
      receiveCommand(command)
      if (remainingUntilSnapshot <= 0) {
        saveSnapshot(state)
        context.become(receiveCommandDecrement(commandsPerSnap))
      } else
        context.become(receiveCommandDecrement(remainingUntilSnapshot - 1))
  }
  
  private[this] def receiveCommand(command: CommandMessage) = command match {
    case Update(updated) =>
      if (updated.content.title != null && !updated.content.title.isEmpty) persist(TodoChanged(updated.id, updated.content))(receiveEvent)
    case Delete(id) =>
      persist(TodoDeleted(id))(receiveEvent)
    case Create(newTodo) =>
      val maybeTodo = state.get(newTodo.id)
      if (maybeTodo.isEmpty) persist(TodoAdded(newTodo.id,newTodo.content))(receiveEvent)
      else maybeTodo.map { todo => sender ! CreateResponse(Right(todo)) }
    case Get(id) =>
      sender ! GetResponse(state.get(id))
    case MostRecent(count) =>
      sender ! MostRecentResponse(state.mostRecent(count))
  }

  override val receiveRecover: Receive = {
    case event: EventMessage => persist(event)(receiveEvent)
    case SnapshotOffer(_, snapshot: ProcessorState) => state = snapshot
  }
  
  private[this] def receiveEvent(event: EventMessage) = { 
    state = state(event)
    event match {
      case TodoAdded(id,_) => sender ! CreateResponse(Left(id))
      case _ => sender ! UpdateResponse(true)
    }
  }
}
