package net.bs

import spray.routing.HttpService
import spray.http._
import StatusCodes._
import MediaTypes._
import akka.actor.Actor
import spray.routing.directives._
import spray.routing._
import Directives._
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import net.bs.models._
import spray.httpx.marshalling._
import spray.json.DefaultJsonProtocol
import akka.event.Logging
import akka.actor.ActorLogging
import akka.event.Logging._
import reflect.ClassTag
import spray.http.HttpHeaders._
import spray.http.StatusCodes._
import spray.util.LoggingContext
import scala.util.Failure
import spray.httpx.SprayJsonSupport._
import scala.concurrent.ExecutionContext.Implicits.global

object MyJsonProtocol extends DefaultJsonProtocol {
  implicit val TodoFormat = jsonFormat3(TodoDto.apply)
}

import MyJsonProtocol._

class TodoServiceActor(val repository: TodoRepositoryQuery with TodoRepositoryCommand, val defaultPageSize: Int) extends Actor with TodoService with ActorLogging {

  import TodoRepositoryProcessor._

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive =
    runRoute(todoRoute)

}

trait TodoService extends HttpService {

  import TodoId._
  import TodoDto._
  import TodoRepositoryProcessor._
  
  import scala.concurrent.duration._

  val defaultPageSize: Int
  val repository: TodoRepositoryQuery with TodoRepositoryCommand
  implicit val timeout = Timeout(3.seconds)

  def logAndFail(ctx: RequestContext, e: Throwable)(implicit log: LoggingContext) {
    log.error(e, "Request {} could not be handled normally", ctx.request)
    ctx.complete(InternalServerError)
  }

  implicit def myExceptionHandler(implicit log: LoggingContext) =
    ExceptionHandler {
      case e: Exception => ctx =>
        logAndFail(ctx, e)
    }

  val todoRoute = {
    logRequestResponse(("todo service req/resp", akka.event.Logging.DebugLevel)) {
      pathPrefix("api") {
        path("todo" / "\\w+".r) { id =>
          get { ctx =>
            repository.show(id)
              .onComplete {
                case Success(resp) =>
                  resp.todo match {
                    case None => ctx.complete(NotFound)
                    case Some(todo) => ctx.complete(todo)
                  }
                case Failure(e) =>
                  logAndFail(ctx, e)
              }
          } ~
            put {
              entity(as[TodoDto]) { todo =>
                ctx => {
                  repository.update(todo)
                    .onComplete {
                      case Success(resp) =>
                        if (resp.updated) ctx.complete(OK)
                        else ctx.complete(NotFound)
                      case Failure(e) =>
                        logAndFail(ctx, e)
                    }
                }
              }
            } ~
            delete {
              ctx =>
                repository.delete(id)
                  .onComplete {
                    case Success(resp) =>
                      if (resp.updated) ctx.complete(OK)
                      else ctx.complete(NotFound)
                    case Failure(e) =>
                      logAndFail(ctx, e)
                  }
            }
        } ~
          path("todo") {
            get {
                parameters('pageSize.?) { pageSize =>
                  val pgSz = pageSize.getOrElse(defaultPageSize.toString).toInt
              ctx =>
                  repository.list(pgSz)
                    .onComplete {
                      case Success(resp) => ctx.complete(resp.todos)
                      case Failure(e) =>
                        logAndFail(ctx, e)
                    }
                }
            } ~
              post {
                entity(as[TodoDto]) { todo =>
                  ctx => {
                    repository.create(todo)
                      .onComplete {
                        case Success(resp) =>
                          resp.todoOrId match {
                            case Left(id) =>
                              ctx.redirect("%s/%s".format(ctx.request.uri, id), SeeOther)
                            case Right(todo) =>
                              ctx.complete(Created, List(Location("%s/%s".format(ctx.request.uri, todo.id.get))), todo)
                          }
                        case Failure(e) =>
                          logAndFail(ctx, e)
                      }
                  }
                }
              }
          }
      } 
    } ~ getFromResourceDirectory("public")
  }
}