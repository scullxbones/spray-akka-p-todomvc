package net.bs

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import spray.testkit.ScalatestRouteTest
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import akka.testkit.TestActorRef
import akka.actor.Props
import akka.testkit.TestProbe
import scala.concurrent.duration._
import akka.actor._
import scala.concurrent.Future
import net.bs.models._
import akka.testkit.TestProbe
import akka.util.Timeout
import akka.testkit.TestActor
import MyJsonProtocol._
import spray.httpx.SprayJsonSupport._
import spray.http.HttpHeaders.Location
import org.scalatest.Matchers
import net.bs.models.TodoRepository._
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import org.mockito.Matchers.{any,eq => eql}
import net.bs.models.TodoRepository._
import scala.reflect.ClassTag
import scala.concurrent.Promise
import scala.concurrent.ExecutionContext

@RunWith(classOf[JUnitRunner])
class TodoServiceActorSpec extends FunSpec with Matchers with ScalatestRouteTest with TodoService with MockitoSugar {
  def actorRefFactory = system // connect the DSL to the test ActorSystem
  val route = todoRoute 
  implicit val _system = system

  val defaultPageSize: Int = 5
  
  trait CombinedRepository extends TodoRepositoryQuery with TodoRepositoryCommand

  val repository = mock[CombinedRepository]

  val todoDto = TodoDto(None,"TEST",true)
  
  def success[T](value: T) =
    Promise[T]().success(value).future
    
  def failure[T](exc: Throwable) =
    Promise[T]().failure(exc).future
    
  describe("the service show") {
    it("should return a todo if it exists") { 
	  when(repository.show(eql("TEST"))(any[ExecutionContext])).thenReturn(success(ShowResponse(Some(todoDto))))
      Get("/api/todo/TEST") ~> route ~> check {
        val todo = responseAs[String]
        todo should include ("title")
        todo should include ("TEST")
      }
    } 
    it("should return a 404 if it does not exist") {
	  when(repository.show(eql("TEST"))(any[ExecutionContext])).thenReturn(success(ShowResponse(None)))
      Get("/api/todo/TEST") ~> route ~> check {
        status.intValue should equal(404)
      }
    }
    it("should return a 500 if the future errors out") {
	  when(repository.show(eql("TEST"))(any[ExecutionContext])).thenReturn(failure(new RuntimeException))
      Get("/api/todo/TEST") ~> route ~> check {
        status.intValue should equal(500)
      }
    }
  }
  
  describe("the service update") {
    val todo = TodoDto(Some("TEST"),"new title",true)
    it("should return a 200 on successful update of an existing todo") {
      when(repository.update(eql(todo))(any[ExecutionContext])).thenReturn(success(Updated(true)))
      Put("/api/todo/TEST",todo) ~> route ~> check {
        status.intValue should equal(200)
      }
    }
    it("should return a 404 on an attempt to update a todo that doesn't exist") {
      when(repository.update(eql(todo))(any[ExecutionContext])).thenReturn(success(Updated(false)))
      Put("/api/todo/TEST",todo) ~> route ~> check {
        status.intValue should equal(404)
      }
    }
    it("should return a 500 if the future errors out") {
      when(repository.update(eql(todo))(any[ExecutionContext])).thenReturn(failure(new RuntimeException))
      Put("/api/todo/TEST",todo) ~> route ~> check {
        status.intValue should equal(500)
      }
    }
  }

  describe("the service delete") {
    it("should return a 200 on successful delete of an existing todo") {
      when(repository.delete(eql("TEST"))(any[ExecutionContext])).thenReturn(success(Updated(true)))
      Delete("/api/todo/TEST") ~> route ~> check {
        status.intValue should equal(200)
      }
    }
    it("should return a 404 on an attempt to delete a todo that doesn't exist") {
      when(repository.delete(eql("TEST"))(any[ExecutionContext])).thenReturn(success(Updated(false)))
      Delete("/api/todo/TEST") ~> route ~> check {
        status.intValue should equal(404)
      }
    }
    it("should return a 500 if the future errors out") {
      when(repository.delete(eql("TEST"))(any[ExecutionContext])).thenReturn(failure(new RuntimeException))
      Delete("/api/todo/TEST") ~> route ~> check {
        status.intValue should equal(500)
      }
    }
  }
  
  describe("the service list") {
    val todo2 = TodoDto(Some("TEST"),"title2",true)
    
    it("should return a populated list of every todo when some exist") {
      when(repository.list(eql(5))(any[ExecutionContext])).thenReturn(success(ListResponse(Seq(todo2))))
      Get("/api/todo") ~> route ~> check {
        status.intValue should equal(200)
        val todoList = responseAs[Seq[TodoDto]]
        todoList should have size 1
        val todo = todoList(0)
        todo.title should be ("title2")
        todo.id should be (Some("TEST"))
      }
    }

    it("should return an empty list with no todos") {
      when(repository.list(eql(5))(any[ExecutionContext])).thenReturn(success(ListResponse(Seq.empty)))
      Get("/api/todo") ~> route ~> check {
        status.intValue should equal(200)
        val todoList = responseAs[Seq[TodoDto]]
        todoList should have size 0
      }
    }

    it("should support a pageSize query parameter") {
      when(repository.list(eql(10))(any[ExecutionContext])).thenReturn(success(ListResponse(Seq.empty)))
      Get("/api/todo?pageSize=10") ~> route ~> check {
        status.intValue should equal(200)
        val todoList = responseAs[Seq[TodoDto]]
        todoList should have size 0
      }
    }

    it("should return a 500 if the future errors out") {
      when(repository.list(eql(5))(any[ExecutionContext])).thenReturn(failure(new RuntimeException))
      Get("/api/todo") ~> route ~> check {
        status.intValue should equal(500)
      }
    }

  }
  
  describe("the service create") {
    val todo = TodoDto(Some("TEST"),"new title",true)
    
    it("when the todo does exist, should perform a 303 see other redirect") {
      when(repository.create(eql(todo))(any[ExecutionContext])).thenReturn(success(Created(Left("TEST"))))
      Post("/api/todo",todo) ~> route ~> check {
        status.intValue should be (303)
        response.header[Location] should be (Some(Location("http://example.com/api/todo/TEST")))
      }
      
      
    }
    
    it("when the todo does not exist, should present the created entity, a 201 created status, and a location redirect") {
      when(repository.create(eql(todo))(any[ExecutionContext])).thenReturn(success(Created(Right(todo))))
      Post("/api/todo",todo) ~> route ~> check {
        status.intValue should be (201)
        response.header[Location] should be (Some(Location("http://example.com/api/todo/TEST")))
        val withId = responseAs[TodoDto]
        withId.title should be ("new title") 
        withId.id should be (Some("TEST"))
      }
    }
    
  }

}