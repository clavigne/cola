package cola

import java.util.concurrent.CompletableFuture

trait BaseEffect:
  self =>
  type A
  type B

  /** delayed is produced by wiring to an FSM */
  def delayed(a: A): CompletableFuture[B]

  def get(a: A): B = delayed(a).get()

trait Effect0[B1] extends BaseEffect:
  type A = Unit
  type B = B1
  def get(): B = get(())
  def delayed(): CompletableFuture[B] = delayed(())
