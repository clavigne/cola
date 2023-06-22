//> using test.dep org.scalameta::munit::0.7.29
//> using javaOpt --enable-preview --add-modules jdk.incubator.concurrent
package cola

import java.util.concurrent.CompletableFuture
import jdk.incubator.concurrent.ScopedValue

object BaseFSMTests:
  class UnitFSM(r: Runnable) extends BaseFSM(r) {
    type Q = Unit
    type A = Unit
    def handlers = ScopedValue.where(UnitFSM.ctx, this)
  }
  object UnitFSM {
    val ctx = ScopedValue.newInstance[UnitFSM]
  }

  class UppercaseFSM(r: Runnable) extends BaseFSM(r) {
    type Q = String
    type A = String
    def handlers = ScopedValue.where(UppercaseFSM.ctx, this)
  }
  object UppercaseFSM {
    val ctx = ScopedValue.newInstance[UppercaseFSM]
  }

  class StringProviderFSM(r: Runnable) extends BaseFSM(r) {
    type Q = Unit
    type A = String
    def handlers = ScopedValue.where(StringProviderFSM.ctx, this)
  }
  object StringProviderFSM {
    val ctx = ScopedValue.newInstance[StringProviderFSM]
  }

class BaseFSMTests extends munit.FunSuite:
  import BaseFSMTests._

  test("join on FSM without pauses") {
    var result: String = ""
    val fsm = new UnitFSM(() => result = "ok")
    fsm.join(identity)
    assertEquals(result, "ok")
  }

  test("join on FSM with multiple pauses") {
    var result: String = ""
    val fsm = new UnitFSM(() => {
      UnitFSM.ctx.get.ask(()).get()
      result = "ok"
      UnitFSM.ctx.get.ask(()).get()
    })
    fsm.join(identity)
    assertEquals(result, "ok")
  }

  test("join on FSM with requests") {
    var result: String = ""
    val fsm = new UppercaseFSM(() => {
      val r1 = UppercaseFSM.ctx.get.ask("hey").get()
      val r2 = UppercaseFSM.ctx.get.ask(" sup").get()
      result = r1 + r2
    })
    fsm.join((s: String) => s.toUpperCase)
    assertEquals(result, "HEY SUP")
  }

  test("join on FSM with multiple simultaneous requests") {
    var result: String = ""
    val fsm = new UppercaseFSM(() => {
      val requests = Seq("l", "o", "l").map(UppercaseFSM.ctx.get.ask)
      val answers = requests.map(_.get())
      result = answers.mkString("")
    })
    fsm.join((s: String) => s.toUpperCase)
    assertEquals(result, "LOL")
  }

  test("join on nested FSMs") {
    var result: String = ""
    val fsm = new StringProviderFSM(() => {
      val fsm = new UppercaseFSM(() => {
        val requests = (0 until 3).map(_ => StringProviderFSM.ctx.get.ask(()))
        val toUpper = requests.map(_.thenCompose(UppercaseFSM.ctx.get.ask))
        result = toUpper.map(_.get()).mkString(" ")
      }).join((s: String) => s.toUpperCase)
    }).join((Unit) => "ok")
    assertEquals(result, "OK OK OK")
  }
