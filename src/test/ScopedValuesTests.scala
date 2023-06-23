package cola
import jdk.incubator.concurrent.ScopedValue
import java.lang.Thread

object ScopedValuesTests:
  object Target:
    val tweak: ScopedValue[Int] = ScopedValue.newInstance

  abstract class WithTarget(r: Runnable) {
    def name: String = ""
    def carrier: ScopedValue.Carrier

    val t = Thread
      .ofVirtual()
      .unstarted(() => carrier.run(r))

    def run() = {
      t.start()
      t.join()
    }
  }

  object WithTarget {
    val selfTarget: ScopedValue[WithTarget] = ScopedValue.newInstance
  }

class ScopedValuesTests extends munit.FunSuite:
  import ScopedValuesTests._

  test("scoped values where") {
    var out = 0
    ScopedValue.where(
      Target.tweak,
      100,
      (() => out = Target.tweak.get()): Runnable
    )
    assertEquals(out, 100)
  }

  test("scoped values carrier") {
    var out = 0
    val carrier = ScopedValue.where(Target.tweak, 99)
    carrier.run((() => out = Target.tweak.get()): Runnable)
    assertEquals(out, 99)
  }

  test("carrier run in other thread") {
    var out = 0
    val carrier = ScopedValue.where(Target.tweak, 103)
    val runnable: Runnable = () => out = Target.tweak.get()
    val t = Thread
      .ofVirtual()
      .unstarted(() => carrier.run(runnable))
    t.start()
    t.join()
    assertEquals(out, 103)
  }

  test("carrier run in class ctor") {
    var out = 0
    val runnable: Runnable = () => out = Target.tweak.get()
    new WithTarget(runnable) {
      def carrier = ScopedValue.where(Target.tweak, 101)
    }.run()
    assertEquals(out, 101)
  }

  test("scoped value points at self") {
    var out = ""
    val runnable: Runnable = () => out = WithTarget.selfTarget.get().name
    new WithTarget(runnable) {
      override def name = "this one"
      def carrier = ScopedValue.where(WithTarget.selfTarget, this)
    }.run()
    assertEquals(out, "this one")
  }
