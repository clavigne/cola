import cola.*

import java.lang.InheritableThreadLocal

object LocallyTests:
  // The Async effect is just defined as an effect returning Unit.
  trait Async extends Effect0[Unit]:
    type B = Unit
    def pause(): Unit = get()

  object Async:
    // Default handler for async functions
    given Handler[Async] = (_: Async) => (_: Unit) => ()

    // In an async context, you can use Async.pause() to relinquish control to
    // the scheduler.
    def pause()(using Async): Unit = summon[Async].pause()

    // What if you are not in a context? That's fine, we can "colour" in the
    // colourless functions using Loom.
    val context = new Locally[Async]

    // and now we need to wire everything into a finite state machine...
    def async[R](f: Async ?=> R) =
      new FSM(f) with Async with context.Hooks

  object Colourless:
    def onEven(i: Int) = if i % 2 == 0 then Async.context.option.map(_.pause())

class LocallyTests extends munit.FunSuite:
  import LocallyTests.*
  import Async.*

  test("test outside of async"):
    assertEquals(Async.context.option, None)

  test("test inside of async"):
    async:
      assert(Async.context.option != None)

  test("test colourless call"):
    var pauses = 0
    given Handler[Async] = _ => _ => pauses += 1

    async:
      (0 until 100).foreach(Colourless.onEven)
    .eval

    assertEquals(pauses, 50)

  test("test colourless calls, multiple nested asyncs"):
    var pauses = 0
    given Handler[Async] = _ => _ => pauses += 1

    async:
      (0 until 10).foreach(Colourless.onEven)

      (0 until 3).foreach {
        i =>
          async:
            (0 until 10).foreach(Colourless.onEven)
          .eval
      }
    .eval

    assertEquals(pauses, 20)
