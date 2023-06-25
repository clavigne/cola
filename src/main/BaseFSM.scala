package cola

import java.lang.Thread
import jdk.incubator.concurrent.ScopedValue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.lang.InheritableThreadLocal

abstract class BaseFSM[E <: BaseEffect](f: E ?=> Unit):
  val instance: E // provides an instance of the effect, wired to this FSM

  // These always run before and after f, in the FSM's own thread. Use them to set handlers.
  def onEntry(): Unit = ()
  def onExit(): Unit = ()

  // Queries
  // Messages from the FSM
  enum Message:
    def send(): Unit = messages.put(this)

    // call thread.start() to start the FSM
    case Init(thread: Thread)

    // answer the query by completing a to resume the FSM
    case Query(query: instance.A, answer: CompletableFuture[instance.B])

    // FSM is done
    case Done

    // FSM is terminated due to an uncaught exception
    case UncaughtException(e: Throwable)
  end Message

  import Message.*

  val messages = new LinkedBlockingQueue[Message]

  private val thread =
    Thread
      .ofVirtual()
      .uncaughtExceptionHandler((_, e) => UncaughtException(e).send())
      .unstarted {
        () =>
          onEntry()
          try
            given E = instance
            f
            Done.send()
          finally onExit()
      }
  Init(thread).send()

  def future(q: instance.A): CompletableFuture[instance.B] =
    val todo = new CompletableFuture[instance.B]()
    Query(q, todo).send()
    todo

  // ---------------------------------------------------------------------------------------------
  // Return the state of the FSM. Note that requests should *not* be lost if the FSM is to
  // proceed.
  def take(): Message = messages.take()

  // Step through the FSM with a given generic handler.
  def run(using handler: Handler[instance.type])(): Unit =
    while true do
      val state = take()
      state match
        case Init(t) => t.start()
        case Query(q, a) =>
          try
            val result = handler(instance)(q)
            a.complete(result)
          catch
            case e => a.completeExceptionally(e)
        case UncaughtException(e) => throw e
        case Done                 => return
