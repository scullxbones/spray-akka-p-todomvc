package net.bs.models

import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FunSpec
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import akka.testkit.TestActorRef
import org.scalatest.mock.MockitoSugar
import akka.actor.Props
import akka.actor.ActorSystem
import org.mockito.Mockito._
import akka.pattern.ask
import scala.concurrent.duration._
import akka.util.Timeout
import scala.concurrent.Future
import scala.concurrent.Await
import scala.util.Success
import scala.util.Failure
import TodoRepositoryActor._
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll
import net.bs.TodoBaseTest

@RunWith(classOf[JUnitRunner])
class TodoRepositoryActorSpec extends TodoBaseTest with BeforeAndAfterAll {

  trait Fixture {
    val repoMock = mock[TodoStore]
    val actorRef = TestActorRef(Props(new TodoRepositoryActor(repoMock)))
  }
  implicit val timeout = Timeout(10)
  implicit val actors = ActorSystem("test")
  
  override def afterAll() {
    actors.shutdown()
  }

  describe("A Todo Repository Actor") {
    it("Should respond to a show request with Some or None depending on whether Todo exists") {
      new Fixture {
        val one = Todo(Some("1"), "title", true)
        when(repoMock.get("1")).thenReturn(Some(one))
        when(repoMock.get("2")).thenReturn(None)

        val actualOne = actorRef ? ShowMessage("1")

        actualOne.onSuccess(_ match {
          case m: ShowResponse => {
            m.todo match {
              case Some(todo) => Some("1") should equal(todo.id)
              case None => fail("Expected to find todo")
            }
          }
        })

        val actualTwo = actorRef ? ShowMessage("2")
        actualTwo.onSuccess(_ match {
          case m: ShowResponse => {
            m.todo match {
              case Some(_) => fail("Expected not to find todo")
              case None => // Success
            }
          }
        })
      }
    }

    it("Should respond to a list request with a list of the existing Todos") {
      new Fixture {
        val todos = List(Todo(Some("1"), "title", true), Todo(Some("2"), "title2", true), Todo(Some("3"), "title3", true))
        when(repoMock.values).thenReturn(todos)

        val actualOne = actorRef ? ListMessage
        actualOne.onSuccess(_ match {
          case m: ListResponse => m.todos should equal(todos)
        })
      }
    }
    
    it ("Should respond to a create request with the created key") {
      new Fixture {
    	val todo = Todo(None, "title", true)
    	val actual = actorRef ? CreateMessage(todo)
    	actual.onSuccess(_ match {
    	  case m:CreateResponse => m.todoOrId match {
    	    case Right(todo) => "one" should equal(todo.id.get)
    	    case _ => fail("Expected success")
    	  }
    	})
      }
    }
    
    it ("Should respond to a update request with a count of how many rows were updated") {
      new Fixture {
    	val todo = Todo(None, "title", true)
    	when(repoMock.+(todo)).thenReturn(repoMock)
    	
    	val actual = actorRef ? UpdateMessage(todo)
    	actual.onSuccess(_ match {
    	  case m:UpdateResponse => m.updated should equal(true)
    	})
      }
    }
    
    it ("Should respond to a delete request with a count of how many rows were deleted") {
      new Fixture {
    	val todo = Todo(None, "title", true)
    	when(repoMock.-(todo)).thenReturn(repoMock)
    	
    	val actual = actorRef ? DeleteMessage("one")
    	actual.onSuccess(_ match {
    	  case m:UpdateResponse => m.updated should equal(true)
    	})
      }
    }
  }
}