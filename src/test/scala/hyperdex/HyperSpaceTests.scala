package hyperdex

import org.scalatest.{BeforeAndAfter, PrivateMethodTester}
import org.scalatest.funsuite.AnyFunSuite

class HyperSpaceTests extends AnyFunSuite with BeforeAndAfter with PrivateMethodTester {

  var simpleHyperspace: HyperSpace = _

  before {
    // 3 axes, 2 cuts each -> 2^3 regions
    simpleHyperspace = new HyperSpace(Seq("a1", "a2"), 8, 2)
  }

  /**
    * testing hyperspace methods isolated
    */
  test("hyperspace should correctly create axis sections") {
    val privateGetAxisSections = PrivateMethod[Seq[Set[Int]]](Symbol("getAxisSections"))
    val numAxes = 3
    val numCuts = 2
    val expectedAmountAxisSections = 6
    val axisSections = simpleHyperspace.invokePrivate(privateGetAxisSections(numAxes, numCuts))
    val amountAxisSections = axisSections.map(_.size).sum
    assert(amountAxisSections == expectedAmountAxisSections)
  }

  test("hyperspace should correctly convert axis sections to regions") {
    val axisSections = Seq(Set(0, 1, 2), Set(0, 1, 2), Set(0, 1, 2))
    val privateAxisSectionsToRegions = PrivateMethod[Set[Region]](Symbol("axisSectionsToRegions"))
    val regions = simpleHyperspace.invokePrivate(privateAxisSectionsToRegions(axisSections))
    assert(regions.size == 27)
  }

  /**
    * testing specific hyperspace object
    */
  test("hyperspace should create correct amount of regions") {
    assert(simpleHyperspace._regionToNodeMapping.size == 8)
  }

  test("hyperspace should assign regions to nodes as evenly distributed as possible") {
    val assignedNodesSet = simpleHyperspace._regionToNodeMapping.values.toSet
    assert(assignedNodesSet.size == 8)
  }

  // AS LONG AS KEY LOOKUPS ARE IMPLEMENTED AS SEARCH (WITHOUT KEY SUBSPACE)
  test("simple hyperspace should return should always return four possible responsible nodes for lookups") {
    val exampleKeys = 1 to 1000
    for (key <- exampleKeys) {
      assert(simpleHyperspace.getResponsibleNodeIds(key).size == 4)
    }
  }
}
