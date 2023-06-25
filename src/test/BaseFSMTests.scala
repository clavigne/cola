package cola

import java.util.concurrent.CompletableFuture
import java.lang.InheritableThreadLocal

// Base tests that aren't using syntactic sugar
object BaseFSMTests:
  trait Async extends BaseEffect:
    type A = Unit
    type B = Unit

  object Async:
    given Handler[Async] = (_: Async) => (_: Unit) => ()
    def pause()(using Async): Unit = summon[Async].get(())

    class async(f: Async ?=> Unit) extends BaseFSM[Async](f):
      override val instance = new Async:
        override def delayed(a: A) = future(a)

  trait Str extends BaseEffect:
    type A = String
    type B = String

  object Str:
    def morph(str: String)(using Str): String = summon[Str].get(str)

    class str(f: Str ?=> Unit) extends BaseFSM[Str](f):
      override val instance = new Str:
        override def delayed(a: A) = future(a)

class BaseFSMTests extends munit.FunSuite:
  import BaseFSMTests.*

  test("join on FSM without pauses") {
    import Async.*
    var result: String = ""
    async:
      result = "ok"
    .run()

    assertEquals(result, "ok")
  }

  test("join on FSM with pauses") {
    import Async.*
    var result: String = ""
    var pauses = 0
    given Handler[Async] = (_: Async) => (_: Unit) => pauses += 1

    async:
      pause()
      result = "ok"
      pause()
    .run()

    assertEquals(result, "ok")
    assertEquals(pauses, 2)
  }

  test("test nested fsms") {
    import Async.*
    var result = ""
    var pauses = 0
    given Handler[Async] = (_: Async) => (_: Unit) => pauses += 1

    async:
      async:
        pause()
        result = "ok"
      .run()
      pause()
      pause()
    .run()

    assertEquals(result, "ok")
    assertEquals(pauses, 3)
  }

  test("test string transformer") {
    import Str.*
    var result = ""
    val strings = Array("zero", "one", "two", "three")
    given Handler[Str] = (_: Str) => (s: String) => strings(s.toInt)

    str:
      val a = Array(1, 2, 3).map(_.toString).map(morph)
      result = a.mkString(" ")
    .run()

    assertEquals(result, "one two three")
  }
