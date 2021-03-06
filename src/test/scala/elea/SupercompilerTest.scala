package elea

import elea.rewrite._
import elea.term._

class SupercompilerTest extends TestConfig {

  import Util._

  val scc = Simplifier.supercompilation

  "supercompilation" should "unfold fixed-points with constructor arguments" in {
    scc.run(term".app (.Cons a (.Cons b (.Cons c xs))) ys") shouldEqual
      term".Cons a (.Cons b (.Cons c (.app xs ys)))".reduce
  }

  it should "simplify" in {
    val t = scc.run(term".lteq n (.add n m)")
    true shouldBe true
  }

  // All properties in proven_properties.elea should pass
  Program
    .prelude
    .loadURLOld(getClass.getResource("proven_properties.elea")).definitions
    .filterKeys(_.startsWith("prop"))
    .toSeq.sortBy(_._1)
    .foreach { case (propName, propTerm) =>
      it should s"prove $propName in proven_properties.elea" in {
        val propNameVar = propName
        scc.run(propTerm) shouldEqual Logic.Truth
      }
    }

  // All properties in unprovable_properties.elea should fail because they are false
  Program
    .prelude
    .loadURLOld(getClass.getResource("unprovable_properties.elea")).definitions
    .filterKeys(_.startsWith("prop"))
    .toSeq.sortBy(_._1)
    .foreach { case (propName, propTerm) =>
      it should s"fail to prove $propName in unprovable_properties.elea" in {
        scc.run(propTerm) should not equal Logic.Truth
      }
    }

  // All properties in unproven_properties.elea should fail, but if they ever pass it's a good thing!
  Program
    .prelude
    .loadURLOld(getClass.getResource("unproven_properties.elea")).definitions
    .filterKeys(_.startsWith("prop"))
    .toSeq.sortBy(_._1)
    .foreach { case (propName, propTerm) =>
      it should s"fail to prove $propName in unproven_properties.elea" in {
        scc.run(propTerm) should not equal Logic.Truth
      }
    }
}
