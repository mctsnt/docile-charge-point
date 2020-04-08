package chargepoint.docile
package test

import com.thenewmotion.ocpp.VersionFamily
import dsl.OcppTest

/** The result of loading a script file: test name and a factory to create a
  * runnable OcppTest instance.
  */
case class TestCase[VFam <: VersionFamily](name: String, test: () => OcppTest[VFam])

