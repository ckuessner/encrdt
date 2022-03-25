package de.ckuessner
package encrdt.crdts

import encrdt.causality.{CausalContext, DotStore}
import encrdt.causality.DotStore.{DotMap, DotSet, dotSetDotStore}
import encrdt.crdts.DeltaAddWinsSet.DeltaAddWinsSetLattice
import encrdt.crdts.interfaces.SetCrdt
import encrdt.lattices.{Causal, SemiLattice}

import de.ckuessner.encrdt.causality.impl.ArrayCausalContext

class DeltaAddWinsSet[E](val replicaId: String,
                         initialState: DeltaAddWinsSetLattice[E] = Causal.bottom[DotMap[E, DotSet]]
                        ) extends SetCrdt[E] {

  private var _state: DeltaAddWinsSetLattice[E] = initialState
  private var deltas: Vector[DeltaAddWinsSetLattice[E]] = Vector()

  def state: DeltaAddWinsSetLattice[E] = _state

  override def add(element: E): Unit =
    mutate(DeltaAddWinsSet.deltaAdd(replicaId, element, _state))

  override def remove(element: E): Unit =
    mutate(DeltaAddWinsSet.deltaRemove(element, _state))

  override def values: Set[E] =
    _state.dotStore.keySet

  private def mutate(delta: DeltaAddWinsSetLattice[E]): Unit = {
    deltas = deltas.appended(delta)
    _state = SemiLattice[DeltaAddWinsSetLattice[E]].merged(_state, delta)
    // TODO: This is the place to hook in anti entropy
  }
}

// See: Delta state replicated data types (https://doi.org/10.1016/j.jpdc.2017.08.003)
object DeltaAddWinsSet {
  type DeltaAddWinsSetLattice[E] = Causal[DotMap[E, DotSet]]

  /**
   * Returns the '''delta''' that adds the element to the `set`.
   *
   * '''Doesn't return the full set, only the delta!'''
   *
   * @param replicaId Id of the replica that performs the add
   * @param element   Element to add
   * @param set       State before the add
   * @tparam E Type of the elements in the set
   * @return The delta of the add
   */
  def deltaAdd[E](replicaId: String,
                  element: E,
                  set: DeltaAddWinsSetLattice[E]
                 ): DeltaAddWinsSetLattice[E] = {

    val newDot = set.causalContext.clockOf(replicaId).advance(replicaId)
    val deltaDotStore: DotMap[E, DotSet] = Map(element -> ArrayCausalContext.single(newDot))
    val deltaCausalContext = CausalContext(set.dotStore.getOrElse(element, DotStore[DotSet].bottom).add(newDot.replicaId, newDot.time))
    Causal(deltaDotStore, deltaCausalContext)
  }

  /**
   * Returns the '''delta''' that removes the element from the `set`.
   *
   * '''Doesn't return the full set, only the delta!'''
   *
   * @param element Element to remove
   * @param set     State before the remove
   * @tparam E Type of the elements in the set
   * @return The delta of the remove
   */
  def deltaRemove[E](element: E, set: DeltaAddWinsSetLattice[E]): DeltaAddWinsSetLattice[E] = Causal(
    DotStore[DotMap[E, DotSet]].bottom,
    CausalContext(set.dotStore.getOrElse(element, DotStore[DotSet].bottom))
  )

  /**
   * Returns the '''delta''' that removes all elements from the `set`.
   *
   * '''Doesn't return the full set, only the delta!'''
   *
   * @param set State before the removal of all elements
   * @tparam E Type of the elements in the set
   * @return The delta of the clear
   */
  def deltaClear[E](set: DeltaAddWinsSetLattice[E]): DeltaAddWinsSetLattice[E] = Causal(
    DotStore[DotMap[E, DotSet]].bottom,
    CausalContext(DotStore[DotMap[E, DotSet]].dots(set.dotStore))
  )
}
