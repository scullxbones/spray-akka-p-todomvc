package net.bs

import scala.concurrent.duration.DurationInt

import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.io.IO
import akka.util.Timeout
import net.bs.models.TodoRepositoryImpl
import net.bs.models.TodoRepositoryProcessor
import spray.can.Http

object Boot extends App {
  
  implicit val system = ActorSystem()
	
  val host = "0.0.0.0"
    
  val port = Option(System.getenv("PORT")).getOrElse("8080").toInt

  // create child repo actor
  val repoActor = system.actorOf(Props(new TodoRepositoryProcessor),"todo-repository")
  
  val repos = new TodoRepositoryImpl(repoActor,Timeout(3.seconds))
  
  // create and start our service actor
  val service = system.actorOf(Props(new TodoServiceActor(repos,50)), "todo-service")
  
  IO(Http) ! Http.Bind(service, interface = host, port = port)
}


