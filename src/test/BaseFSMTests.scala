package cola

import java.util.concurrent.CompletableFuture
import java.lang.InheritableThreadLocal

object BaseFSMTests:
  class UnitFSM(f: UnitFSM => Unit) extends BaseFSM[UnitFSM](f):
    type Q = Unit
    type A = Unit

  class UppercaseFSM(f: UppercaseFSM => Unit) extends BaseFSM[UppercaseFSM](f):
    type Q = String
    type A = String

  class StringProviderFSM(f: StringProviderFSM => Unit) extends BaseFSM[StringProviderFSM](f):
    type Q = Unit
    type A = String

class BaseFSMTests extends munit.FunSuite:
  import BaseFSMTests.*

  test("join on FSM without pauses") {
    var result: String = ""
    val fsm = new UnitFSM(_ => result = "ok")
    fsm.join(identity)
    assertEquals(result, "ok")
  }

  test("join on FSM with multiple pauses") {
    var result: String = ""
    val fsm = new UnitFSM(
      ctx =>
        ctx.ask(())
        result = "ok"
        ctx.ask(())
    )
    fsm.join(identity)
    assertEquals(result, "ok")
  }

  test("join on FSM with requests") {
    var result: String = ""
    val fsm = new UppercaseFSM(
      ctx =>
        val r1 = ctx.ask("hey").get()
        val r2 = ctx.ask(" sup").get()
        result = r1 + r2
    )
    fsm.join((s: String) => s.toUpperCase)
    assertEquals(result, "HEY SUP")
  }

  test("join on FSM with multiple simultaneous requests") {
    var result: String = ""
    val fsm = new UppercaseFSM(
      ctx =>
        val requests = Seq("l", "o", "l").map(ctx.ask)
        val answers = requests.map(_.get())
        result = answers.mkString("")
    )
    fsm.join((s: String) => s.toUpperCase)
    assertEquals(result, "LOL")
  }

  test("join on nested FSMs") {
    var result: String = ""
    val fsm = new StringProviderFSM(
      provider =>
        val fsm = new UppercaseFSM(
          upper =>
            val requests = (0 until 3).map(_ => provider.ask(()))
            val toUpper = requests.map(_.thenCompose(upper.ask))
            result = toUpper.map(_.get()).mkString(" ")
        ).join((s: String) => s.toUpperCase)
    ).join(Unit => "ok")
    assertEquals(result, "OK OK OK")
  }
