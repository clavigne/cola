package cola

import java.util.concurrent.CompletableFuture
import java.lang.InheritableThreadLocal

object BaseFSMTests:
  object Async:
    object Effect extends cola.Effect:
      type Q = Unit
      type A = Unit

    type Effect = Effect.type
    val handler: Handler[Effect] = (_: Effect) => (_: Unit) => ()
    class FSM(f: Context[Effect] => Unit) extends BaseFSM[Effect](f)

  object Str:
    object Effect extends cola.Effect:
      type Q = String
      type A = String

    type Effect = Effect.type
    def handler(f: String => String): Handler[Effect] =
      (_: Effect) => f
    class FSM(f: Context[Effect] => Unit) extends BaseFSM[Effect](f)

class BaseFSMTests extends munit.FunSuite:
  import BaseFSMTests.*

  test("join on FSM without pauses") {
    var result: String = ""
    val fsm = Async.FSM(_ => result = "ok")
    fsm.join(Async.handler)
    assertEquals(result, "ok")
  }

  test("join on FSM with multiple pauses") {
    var result: String = ""
    val fsm = Async.FSM(
      ctx =>
        ctx.suspend(Async.Effect)(()).get()
        result = "ok"
        ctx.suspend(Async.Effect)(()).get()
    )
    fsm.join(Async.handler)
    assertEquals(result, "ok")
  }

  test("join on FSM with requests") {
    var result: String = ""
    val fsm = new Str.FSM(
      ctx =>
        val r1 = ctx.suspend(Str.Effect)("hey").get()
        val r2 = ctx.suspend(Str.Effect)(" sup").get()
        result = r1 + r2
    )
    fsm.join(Str.handler(_.toUpperCase))
    assertEquals(result, "HEY SUP")
  }

  test("join on FSM with multiple simultaneous requests") {
    var result: String = ""
    val fsm = new Str.FSM(
      ctx =>
        val requests = Seq("l", "o", "l").map(ctx.suspend(Str.Effect))
        val answers = requests.map(_.get())
        result = answers.mkString("")
    )
    fsm.join(Str.handler(_.toUpperCase))
    assertEquals(result, "LOL")
  }

  test("join on nested FSMs") {
    var result: String = ""

    val fsm = new Str.FSM(
      provider =>
        val fsm = new Str.FSM(
          uppercase =>
            val requests = (0 until 3).map(_ => provider.suspend(Str.Effect)(""))
            val upper = requests.map(_.thenCompose(uppercase.suspend(Str.Effect)))
            result = upper.map(_.get()).mkString(" ")
        ).join(Str.handler(_.toUpperCase))
    ).join(Str.handler(_ => "lol"))
    assertEquals(result, "LOL LOL LOL")
  }
