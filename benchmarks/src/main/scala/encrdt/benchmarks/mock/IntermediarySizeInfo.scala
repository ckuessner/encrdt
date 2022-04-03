package de.ckuessner
package encrdt.benchmarks.mock

trait IntermediarySizeInfo {
  def sizeInBytes: Long
  def encDeltaCausalityInfoSizeInBytes: Long
  def rawDeltasSizeInBytes: Long

  def numberStoredDeltas: Int
}
