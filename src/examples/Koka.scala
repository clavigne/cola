/* Scala version of code in
 *
 *    https://koka-lang.github.io/koka/doc/book.html#why-handlers
 *
 */

import cola.*

trait Yield extends Effect1[Int, Boolean]:
  def apply(i: Int): Boolean = get(i)

object Yield:
  def eval[R](using Handler[Yield])(f: Yield ?=> R): R = 
    (new FSM(f) with Yield).eval

def traverse(xs: List[Int])(using yields: Yield): Unit =
  xs match
    case x :: xx => if yields(x) then traverse(xx) else ()
    case Nil     => ()

@main def printElems(): Unit =
  given Handler[Yield] = (_: Yield) => (i) =>
    println("yielded " + i.toString)
    i <= 2

  Yield.eval:
    traverse(List(1,2,3,4))

