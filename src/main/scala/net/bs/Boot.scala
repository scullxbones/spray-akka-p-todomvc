package net.bs

import akka.actor.Props
import java.net.URI
import net.bs.models.TodoRepositoryActor
import akka.actor.ActorSystem
import akka.io.IO
import spray.can.Http

object Boot extends App {
  
  implicit val system = ActorSystem()
	
  val host = "0.0.0.0"
    
  val port = Option(System.getenv("PORT")).getOrElse("8080").toInt

  // create child repo actor
  val repoActor = system.actorOf(Props[TodoRepositoryActor],"todo-repository")
  
  // create and start our service actor
  val service = system.actorOf(Props(new TodoServiceActor(repoActor)), "todo-service")
  
  IO(Http) ! Http.Bind(service, interface = host, port = port)
}


