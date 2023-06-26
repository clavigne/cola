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

```scala
import cola.*
```
