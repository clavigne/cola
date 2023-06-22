//> using test.dep org.scalameta::munit::0.7.29
//> using javaOpt --enable-preview --add-modules jdk.incubator.concurrent
package cola

import java.util.concurrent.CompletableFuture

class MyTests extends munit.FunSuite {
  private class UnitFSM(r: Runnable) extends BaseFSM(r) {
    type Q = Unit
    type A = Unit
  }

  test("join on FSM without pauses") {
    var result: String = ""
    val fsm = new UnitFSM(() => result = "ok") 
    fsm.join(identity)
    assertEquals(result, "ok")
  }

  test("join on FSM with multiple pauses") {
    var result: String = ""
    val fsm = new UnitFSM(() => {
      BaseFSM.context.head.asInstanceOf[UnitFSM].ask(()).get()
      result = "ok"
      BaseFSM.context.head.asInstanceOf[UnitFSM].ask(()).get()
    }) 
    fsm.join(identity)
    assertEquals(result, "ok")
  }

  private class UppercaseFSM(r: Runnable) extends BaseFSM(r) {
    type Q = String
    type A = String
  }

  test("join on FSM with back and forth requests") {
    var result: String = ""
    val fsm = new UppercaseFSM(() => {
      val r1 = BaseFSM.context.head.asInstanceOf[UppercaseFSM].ask("hey").get()
      val r2 = BaseFSM.context.head.asInstanceOf[UppercaseFSM].ask(" sup").get()
      result = r1 + r2
    }) 
    fsm.join((s:String) => s.toUpperCase)
    assertEquals(result, "HEY SUP")
  }


  test("join on FSM with simultaneous requests") {
    var result: String = ""
    val fsm = new UppercaseFSM(() => {
      def ask(s: String): CompletableFuture[String] = 
        BaseFSM.context.head.asInstanceOf[UppercaseFSM].ask(s)

      val requests = Seq("l","o","l").map(ask)
      val answers = requests.map(_.get())
      result = answers.mkString("")
    }) 
    fsm.join((s:String) => s.toUpperCase)
    assertEquals(result, "LOL")
  }

}
