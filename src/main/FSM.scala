package cola

import java.lang.Thread
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue

abstract class FSM[E <: BaseEffect, R](f: E ?=> R):
  self: FSM[E, R] with E =>
  // These always run before and after f, in the FSM's own thread.
  def onEntry(): Unit = ()
  def onExit(): Unit = ()

  // Messages from the FSM
  enum Message:
    def send(): Unit = messages.put(this)

    // call thread.start() to start the FSM
    case Init(thread: Thread)

    // answer the query by completing a to resume the FSM
    case Query(query: A, answer: CompletableFuture[B])

    // FSM is done
    case Done(result: R)

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
            given E = this
            val result = f
            Done(result).send()
          finally onExit()
      }
  Init(thread).send()

  // Called from the virtual thread
  override def suspend(q: A): CompletableFuture[B] =
    val todo = new CompletableFuture[B]()
    Query(q, todo).send()
    todo

  override def get(q: A): B = suspend(q).get()

  // ---------------------------------------------------------------------------------------------
  // Return the state of the FSM. Note that requests should *not* be lost if the FSM is to
  // proceed.
  def take(): Message = messages.take()

  // Step through the FSM with a given generic handler.
  def eval(using handler: Handler[E]): R =
    while true do
      val state = take()
      state match
        case Init(t) => t.start()
        case Query(q, a) =>
          try
            val result = handler(this)(q)
            a.complete(result)
          catch case e => a.completeExceptionally(e)
        case UncaughtException(e) => throw e
        case Done(r)              => return r

    // should never happen
    throw new RuntimeException("exited FSM loop without a Done message")
