package cola

import java.lang.Thread
import jdk.incubator.concurrent.ScopedValue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.lang.InheritableThreadLocal

/** The base effect type is basically just a tuple with query and answer types. */
trait Effect:
  type Q
  type A

type Handler[E <: Effect] = (e: E) => e.Q => e.A

abstract class Context[-E <: Effect]:
  def suspend[E1 <: E](e: E1)(q: e.Q): CompletableFuture[e.A]

abstract class BaseFSM[E <: Effect](f: Context[E] => Unit) extends Context[E]:
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
    case Query[E1 <: E](val e: E1)(val q: e.Q, val a: CompletableFuture[e.A])

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
            f(this)
            Done.send()
          finally onExit()
      }
  Init(thread).send()

  final override def suspend[E1 <: E](e: E1)(q: e.Q): CompletableFuture[e.A] =
    val future = CompletableFuture[e.A]()
    Query(e)(q, future).send()
    future

  // ---------------------------------------------------------------------------------------------
  // Return the state of the FSM. Note that requests should *not* be lost if the FSM is to
  // proceed.
  def take(): Message = messages.take()

  // Step through the FSM with a given generic handler.
  def join(handler: Handler[E]): Unit =
    while true do
      val state = take()
      state match
        case Init(t)              => t.start()
        case q: Query[?]          => q.a.complete(handler(q.e)(q.q))
        case UncaughtException(e) => throw e
        case Done                 => return
