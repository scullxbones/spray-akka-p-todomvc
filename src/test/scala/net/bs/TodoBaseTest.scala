package net.bs

import org.scalatest.FunSpecLike
import org.scalatest.mock.MockitoSugar
import org.scalatest.Matchers
import scala.concurrent.ExecutionContext

trait TodoBaseTest extends FunSpecLike with Matchers with MockitoSugar

class SameThreadExecutionContext extends ExecutionContext {
    /** Runs a block of code on this execution context.
   */
  def execute(runnable: Runnable): Unit = runnable.run()
  
  /** Reports that an asynchronous computation failed.
   */
  def reportFailure(t: Throwable): Unit = throw t
}
