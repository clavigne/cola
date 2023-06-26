package cola

import java.util.concurrent.CompletableFuture
import java.lang.InheritableThreadLocal

trait BaseEffect:
  self =>
  type A
  type B

  def suspend(a: A): CompletableFuture[B]
  def get(a: A): B

trait Effect0[B1] extends BaseEffect:
  type A = Unit
  type B = B1

  def suspend(): CompletableFuture[B] = suspend(())
  def get(): B = get(())

trait Effect1[A1, B1] extends BaseEffect:
  type A = A1
  type B = B1

// Handlers handle effects in the caller thread
type Handler[E <: BaseEffect] = (effect: E) => (effect.A) => effect.B

// Use to provide "colourless" dynamic bindings of effect.
final class Locally[E <: BaseEffect]:
  private val inner = new InheritableThreadLocal[Seq[E]]()
  inner.set(Nil)
  def option: Option[E] = inner.get().headOption

  trait Hooks:
    self: FSM[E, ?] with E =>
    override def onEntry(): Unit =
      val current = inner.get()
      inner.set(this +: current)

    override def onExit(): Unit =
      val current = inner.get()
      current match
        case h :: tail if h == this => inner.set(tail)
        case _ =>
          throw new RuntimeException("Attempted to teardown a scope that didnt contain this value")
