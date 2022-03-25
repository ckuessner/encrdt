package de.ckuessner
package encrdt.crdts

import encrdt.causality.DotStore
import encrdt.causality.DotStore.{DotFun, dotFunDotStore}
import encrdt.causality.impl.ArrayCausalContext
import encrdt.lattices.{Causal, SemiLattice}

object DeltaMultiValueRegister {
  type DeltaMultiValueRegisterLattice[V] = Causal[DotFun[V]]

  def deltaWrite[V](
      value: V,
      replicaId: String,
      register: DeltaMultiValueRegisterLattice[V]
  ): DeltaMultiValueRegisterLattice[V] = {

    val dot = register.causalContext.clockOf(replicaId).advance(replicaId)
    Causal(
      Map(dot -> value),
      ArrayCausalContext.fromSet(register.dotStore.keySet).add(dot.replicaId, dot.time)
    )
  }

  def deltaClear[V: SemiLattice](register: DeltaMultiValueRegisterLattice[V]): DeltaMultiValueRegisterLattice[V] =
    Causal(
      DotStore[DotFun[V]].bottom,
      ArrayCausalContext.fromSet(register.dotStore.keySet)
    )

  def read[V](register: DeltaMultiValueRegisterLattice[V]): Set[V] = {
    register.dotStore.values.toSet
  }
}
