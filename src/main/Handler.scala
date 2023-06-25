package cola

type Handler[E <: BaseEffect] = (effect: E) => (effect.A) => (effect.B)

