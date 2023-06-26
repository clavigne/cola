## Cola: Stack-safe algebraic effects and handlers for Scala

This is an experiment in defining
[Koka](https://koka-lang.github.io/koka/doc/index.html)-style generic algebraic
effects and handlers in Scala, using Loom virtual threads. The effects are both
stack-safe *and* colourless.

In a nutshell, [algebraic
effects](https://overreacted.io/algebraic-effects-for-the-rest-of-us/) are the
generic form of control structures like async/await or throw/catch. Just like
how generic collections allow users to create their own parametrized
collection, algebraic effects provide users with the power to define their own
complex control structures.

The Koka documentation [describes effect
handlers](https://koka-lang.github.io/koka/doc/book.html#why-handler) using the
following example, 
```koka
effect yield
  ctl yield( i : int ) : bool

fun traverse( xs : list<int> ) : yield () 
  match xs 
    Cons(x,xx) -> if yield(x) then traverse(xx) else ()
    Nil        -> ()

fun print-elems() : console () 
  with ctl yield(i)
    println("yielded " ++ i.show)
    resume(i<=2)
  traverse([1,2,3,4])
```
This program prints
```
yielded: 1
yielded: 2
yielded: 3
```
The equivalent code using this library is,
```Scala
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
```
This is a bit longer, and it has more keywords, but it's pretty close!


