package cola

import java.util.concurrent.CompletableFuture
import java.lang.InheritableThreadLocal

// Base tests that aren't using syntactic sugar
object BasicFSMTests:
  trait Async extends BaseEffect:
    type A = Unit
    type B = Unit

  object Async:
    given Handler[Async] = (_: Async) => (_: Unit) => ()
    def pause()(using Async): Unit = summon[Async].get(())
    def async[R](f: Async ?=> R) = new FSM(f) with Async

  trait Str extends BaseEffect:
    type A = String
    type B = String

  object Str:
    def morph(str: String)(using Str): String = summon[Str].get(str)
    def str[R](f: Str ?=> R) = new FSM(f) with Str

class BasicFSMTests extends munit.FunSuite:
  import BasicFSMTests.*

  test("join on FSM without pauses"):
    import Async.*
    val result: String =
      async:
        "ok"
      .eval

    assertEquals(result, "ok")

  test("join on FSM with pauses"):
    import Async.*
    var pauses = 0
    given Handler[Async] = (_: Async) => (_: Unit) => pauses += 1

    val result = async:
      pause()
      val out = "ok"
      pause()
      out
    .eval

    assertEquals(result, "ok")
    assertEquals(pauses, 2)

  test("test nested fsms") {
    import Async.*
    var result = ""
    var pauses = 0
    given Handler[Async] = (_: Async) => (_: Unit) => pauses += 1

    async:
      async:
        pause()
        result = "ok"
      .eval
      pause()
      pause()
    .eval

    assertEquals(result, "ok")
    assertEquals(pauses, 3)
  }

  test("test string transformer") {
    import Str.*
    val strings = Array("zero", "one", "two", "three")
    given Handler[Str] = (_: Str) => (s: String) => strings(s.toInt)

    val result = str:
      val a = Array(1, 2, 3).map(_.toString).map(morph)
      a.mkString(" ")
    .eval

    assertEquals(result, "one two three")
  }
