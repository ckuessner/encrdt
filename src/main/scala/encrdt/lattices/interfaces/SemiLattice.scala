package de.ckuessner
package encrdt.lattices.interfaces

trait SemiLattice[T] {
  def merged(left: T, right: T): T
}

object SemiLattice {
  @inline def apply[A](implicit lattice: SemiLattice[A]): SemiLattice[A] = lattice

  def merged[A: SemiLattice](left: A, right: A): A = apply[A].merged(left, right)
}