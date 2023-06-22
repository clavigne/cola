package cola

import java.lang.Thread
import jdk.incubator.concurrent.ScopedValue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue

object BaseFSM:
  // FSM stack
  private val ctx: ScopedValue[List[BaseFSM]] = ScopedValue.newInstance()
  def context: List[BaseFSM] = ctx.orElse(Nil)

abstract class BaseFSM(val runnable: Runnable):
  type Q
  type A

  import BaseFSM._

  // State of the FSM, passed through the requests queue
  enum State:
    case Init
    case Request(q: Q, a: CompletableFuture[A])
    case Done

  val requests = new LinkedBlockingQueue[State]
  requests.add(State.Init: State)

  val thread = Thread
    .ofVirtual()
    .unstarted(() =>
      ScopedValue.where(BaseFSM.ctx, this +: BaseFSM.context, runnable)
      requests.add(State.Done: State)
    )

  // called from virtual thread, unsafe
  def ask(q: Q): CompletableFuture[A] = {
    val answer = new CompletableFuture[A]
    val request = State.Request(q, answer)
    requests.put(request)
    answer
  }

  // Return the state of the FSM. Note that requests should *not* be lost if the FSM is to
  // proceed.
  def take(): State = requests.take()

  // Step through the FSM, answering any queries using the given function.
  def join(f: Q => A): Unit = {
    while (true) {
      val state = take()
      state match {
        case State.Init          => thread.start()
        case State.Request(q, a) => a.complete(f(q))
        case State.Done          => return
      }
    }
  }
