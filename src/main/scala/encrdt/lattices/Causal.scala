package de.ckuessner
package encrdt.lattices

import encrdt.causality.DotStore._
import encrdt.causality.{DotStore, VectorClock}

case class Causal[D: DotStore](dotStore: D, causalContext: CausalContext)

// See: Delta state replicated data types (https://doi.org/10.1016/j.jpdc.2017.08.003)
object Causal {
  def bottom[D: DotStore]: Causal[D] = Causal(DotStore[D].bottom, VectorClock())

  implicit def CausalWithDotSetLattice: SemiLattice[Causal[DotSet]] = (left, right) => {
    // (s, c) ⨆ (s', c') = ((s ∩ s') ∪ (s \ c') ∪ (s' \ c), c ∪ c')
    val inBoth = left.dotStore & right.dotStore
    val newInLeft = left.dotStore.filterNot(dot => dot <= right.causalContext.clockOf(dot.replicaId))
    val newInRight = right.dotStore.filterNot(dot => dot <= left.causalContext.clockOf(dot.replicaId))

    val mergedCausalContext = left.causalContext.merged(right.causalContext)
    Causal(inBoth ++ newInLeft ++ newInRight, mergedCausalContext)
  }

  implicit def CausalWithDotFunLattice[V: SemiLattice]: SemiLattice[Causal[DotFun[V]]] = (left, right) => {
    // (m, c) ⨆ (m', c') = ( {k -> m(k) ⨆ m'(k) | k ∈ dom m ∩ dom m'} ∪
    //                       {(d, v) ∈ m  | d ∉ c'} ∪
    //                       {(d, v) ∈ m' | d ∉ c},
    //                      c ∪ c')
    Causal(
      (left.dotStore.keySet union right.dotStore.keySet map { (dot: Dot) =>
        (dot, SemiLattice.merged(left.dotStore(dot), right.dotStore(dot)))
      }).toMap
        ++ left.dotStore.filterNot { case (dot, _) => right.dotStore.contains(dot) }
        ++ right.dotStore.filterNot { case (dot, _) => left.dotStore.contains(dot) },
      left.causalContext.merged(right.causalContext)
    )
  }

  implicit def CausalWithDotMapLattice[K, V: DotStore](implicit vSemiLattice: SemiLattice[Causal[V]]): SemiLattice[Causal[DotMap[K, V]]] = (left, right) => {
    // (m, c) ⨆ (m', c') = ( {k -> v(k) | k ∈ dom m ∩ dom m' ∧ v(k) ≠ ⊥}, c ∪ c')
    //                      where v(k) = fst((m(k), c) ⨆ (m'(k), c'))
    Causal(((left.dotStore.keySet union right.dotStore.keySet) map { key =>
      val leftCausal = Causal(left.dotStore.getOrElse(key, DotStore[V].bottom), left.causalContext)
      val rightCausal = Causal(right.dotStore.getOrElse(key, DotStore[V].bottom), right.causalContext)
      key -> SemiLattice[Causal[V]].merged(leftCausal, rightCausal).dotStore
    } filterNot {
      case (key, dotStore) => DotStore[V].bottom == dotStore
    }).toMap, left.causalContext.merged(right.causalContext))
  }
}