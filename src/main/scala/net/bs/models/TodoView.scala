package net.bs.models

import akka.persistence.View

class TodoView(processorName: String) extends View {
  def processorId: String = processorName
  
  def receive: Receive = {
    case _ =>
  }
  
}