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
import TodoRepository._
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll
import net.bs.TodoBaseTest
import akka.testkit.TestProbe
import net.bs.SameThreadExecutionContext

@RunWith(classOf[JUnitRunner])
class TodoRepositoryActorSpec extends TodoBaseTest with BeforeAndAfterAll {
  import net.bs.models.TodoRepositoryProcessor._

  implicit val timeout = Timeout(3.seconds)
  implicit val actors = ActorSystem("test")
  implicit val ec = new SameThreadExecutionContext
  
  trait Fixture {
    val probe = TestProbe()(actors)
    val underTest = new TodoRepositoryImpl(probe.ref,timeout)
  }
  override def afterAll() {
    actors.shutdown()
  }

  describe("A Todo Repository") {
    it("Should respond to a show request with Some when Todo exists") { new Fixture {
        val todoId = TodoId.generate
        val one = TodoDto(Some(todoId.uuid.toString), "title", true)
        val actual = underTest.show(todoId.uuid.toString)
        probe.expectMsg(3.seconds, Get(todoId))
        probe.reply(GetResponse(Some(one)))
        val result = Await.result(actual, 3.seconds)
        result.todo match {
          case Some(todo) => Some(todoId.uuid.toString) should equal(todo.id)
          case None => fail("Expected to find todo")
        }
    }}
    
    it("Should respond to a show request with None when Todo does not exist") { new Fixture {
        val todoId = TodoId.generate
        val actual = underTest.show(todoId.uuid.toString)
        probe.expectMsg(3.seconds, Get(todoId))
        probe.reply(GetResponse(None))
        val result = Await.result(actual, 3.seconds)
        result.todo match {
          case Some(todo) => fail("Expected not to find todo")
          case None => // Success
        }
      }
    }

    it("Should respond to a list request with a list of the existing Todos") { new Fixture {
        val todos = Seq(Todo(TodoId.generate, TodoContent("title", true)), 
        				Todo(TodoId.generate, TodoContent("title2", true)), 
        				Todo(TodoId.generate, TodoContent("title3", true)))
        val expected = todos.map(TodoDto.todoToDto)
        val actual = underTest.list(5)
        probe.expectMsg(3.seconds, MostRecent(5))
        probe.reply(MostRecentResponse(todos))
        val result = Await.result(actual, 3.seconds)
        result should be (ListResponse(expected))
    } }
    
    it ("Should respond to a create request with the created key") { new Fixture {
        val todoId = TodoId.generate
    	val todo = Todo(todoId, TodoContent("title", true))
    	val dto: TodoDto = TodoDto(Some(todoId.uuid.toString),"title",true)
    	val actual = underTest.create(dto)
    	probe.expectMsg(3.seconds, Create(todo))
    	probe.reply(CreateResponse(Right(todo)))
    	val result = Await.result(actual, 3.seconds)
    	result.todoOrId match { case Left(_) => fail("should be (Right(todo))") case _ => }
    } }
    
    it ("Should respond to a update request with whether anything was updated") { new Fixture {
        val id = TodoId.generate.uuid.toString
    	val todo = TodoDto(Some(id), "title", true)
    	val actual = underTest.update(todo)
    	probe.expectMsg(3.seconds, Update(todo))
    	probe.reply(UpdateResponse(true))
    	val result = Await.result(actual, 3.seconds)
    	result should be (Updated(true))
    } }
    
    it ("Should respond to a delete request with whether anything was deleted") { new Fixture {
      val id = TodoId.generate
    	val actual = underTest.delete(id)
    	probe.expectMsg(3.seconds, Delete(id))
    	probe.reply(UpdateResponse(true))
    	val result = Await.result(actual, 3.seconds)
    	result should be (Updated(true))
    } }
  }
}