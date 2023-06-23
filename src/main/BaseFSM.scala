package cola

import java.lang.Thread
import jdk.incubator.concurrent.ScopedValue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.lang.InheritableThreadLocal

abstract class BaseFSM(f: () => Unit):
  // Things to overload
  type Q
  type A
  def handler: InheritableThreadLocal[BaseFSM]

  // Messages from the FSM
  enum Message:
    def send(): Unit = messages.put(this)

    // call thread.start() to start the FSM
    case Init(thread: Thread)

    // nothing to do
    case Running

    // answer the query by completing a to resume the FSM
    case Request(q: Q, a: CompletableFuture[A])

    // FSM is done
    case Done

    // FSM is terminated due to an uncaught exception
    case UncaughtException(e: Throwable)
  end Message

  import Message._

  val messages = new LinkedBlockingQueue[Message]

  private val thread = {
    Thread
      .ofVirtual()
      .uncaughtExceptionHandler((_, e) => UncaughtException(e).send())
      .unstarted(() => {
        handler.set(this)
        Running.send()
        f()
        handler.remove()
        Done.send()
      })
  }
  Init(thread).send()

  // Ask handler for a request.
  def ask(q: Q): CompletableFuture[A] = {
    val answer = new CompletableFuture[A]
    Request(q, answer).send()
    answer
  }

  // ---------------------------------------------------------------------------------------------
  // Return the state of the FSM. Note that requests should *not* be lost if the FSM is to
  // proceed.
  def take(): Message = messages.take()

  // Step through the FSM, answering any queries using the given function.
  def join(f: Q => A): Unit = {
    while (true) {
      val state = take()
      state match {
        case Init(t)              => t.start()
        case Running              =>
        case Request(q, a)        => a.complete(f(q))
        case UncaughtException(e) => throw e
        case Done                 => return
      }
    }
  }
