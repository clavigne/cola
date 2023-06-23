package cola

import java.util.concurrent.CompletableFuture
import java.lang.InheritableThreadLocal

object BaseFSMTests:
  type Handler = InheritableThreadLocal[BaseFSM]

  class UnitFSM(f: () => Unit) extends BaseFSM(f) {
    type Q = Unit
    type A = Unit
    def handler = UnitFSM.ctx.asInstanceOf[Handler]
  }
  object UnitFSM {
    val ctx = new InheritableThreadLocal[UnitFSM]()
  }

  class UppercaseFSM(f: () => Unit) extends BaseFSM(f) {
    type Q = String
    type A = String
    def handler = UppercaseFSM.ctx.asInstanceOf[Handler]
  }
  object UppercaseFSM {
    val ctx = new InheritableThreadLocal[UppercaseFSM]()
  }

  class StringProviderFSM(f: () => Unit) extends BaseFSM(f) {
    type Q = Unit
    type A = String
    def handler = StringProviderFSM.ctx.asInstanceOf[Handler]
  }
  object StringProviderFSM {
    val ctx = new InheritableThreadLocal[StringProviderFSM]()
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
