package hyperdex

import org.scalatest.{BeforeAndAfter, PrivateMethodTester}
import org.scalatest.funsuite.AnyFunSuite

class HyperSpaceTests extends AnyFunSuite with BeforeAndAfter with PrivateMethodTester {

  var simpleHyperspaceEightNodes: HyperSpace = _
  var simpleHyperspaceOneNode: HyperSpace = _
  var bigHyperSpaceSixNodes: HyperSpace = _

  before {
    // 3 axes, 2 cuts each -> 2^3 regions
    simpleHyperspaceEightNodes = new HyperSpace(Seq("a1", "a2"), 8, 2)
    simpleHyperspaceOneNode = new HyperSpace(Seq("a1", "a2"), 1, 2)
    // 7 axes, 3 cuts each -> 3^7 = 2187 regions
    bigHyperSpaceSixNodes = new HyperSpace(Seq("a1", "a2", "a3", "a4", "a5", "a6"), 6, 3)
  }

  /**
    * testing hyperspace methods isolated
    */
  test("hyperspace should correctly create axis sections") {
    val privateGetAxisSections = PrivateMethod[Seq[Set[Int]]](Symbol("getAxisSections"))
    val numAxes = 3
    val numCuts = 2
    val expectedAmountAxisSections = 6
    val axisSections = simpleHyperspaceEightNodes.invokePrivate(privateGetAxisSections(numAxes, numCuts))
    val amountAxisSections = axisSections.map(_.size).sum
    assert(amountAxisSections == expectedAmountAxisSections)
  }

  test("hyperspace should correctly convert axis sections to regions") {
    val axisSections = Seq(Set(0, 1, 2), Set(0, 1, 2), Set(0, 1, 2))
    val privateAxisSectionsToRegions = PrivateMethod[Set[Region]](Symbol("axisSectionsToRegions"))
    val regions = simpleHyperspaceEightNodes.invokePrivate(privateAxisSectionsToRegions(axisSections))
    assert(regions.size == 27)
  }

  /**
    * testing specific hyperspace object
    */
  test("hyperspace should create correct amount of regions") {
    assert(simpleHyperspaceEightNodes._regionToNodeMapping.size == 8)
    assert(simpleHyperspaceOneNode._regionToNodeMapping.size == 8)
    assert(bigHyperSpaceSixNodes._regionToNodeMapping.size == 2187)
  }

  test("hyperspace should assign regions to nodes as evenly distributed as possible") {
    var assignedNodesSet = simpleHyperspaceEightNodes._regionToNodeMapping.values.toSet
    assert(assignedNodesSet.size == 8)
    assignedNodesSet = simpleHyperspaceOneNode._regionToNodeMapping.values.toSet
    assert(assignedNodesSet.size == 1)

    for (node <- 0 until 6) {
      // 2187 / 6 = 364.5
      val numAssignedRegions = bigHyperSpaceSixNodes._regionToNodeMapping.values.count(_ == node)
      assert(numAssignedRegions == 364 || numAssignedRegions == 365)
    }
  }

  // AS LONG AS KEY LOOKUPS ARE IMPLEMENTED AS SEARCH (WITHOUT KEY SUBSPACE)
  // TODO: does this really hold for all edge cases ? (should it be rounded down?)
  test("""hyperspace should return should always return 
      |at least (1 / <numCuts>) of all nodes (rounded up) 
      |as possibly responsible for lookup""".stripMargin) {
    val exampleKeys = 1 to 1000
    for (key <- exampleKeys) {
      assert(simpleHyperspaceEightNodes.getResponsibleNodeIds(key).size >= 4)
      assert(simpleHyperspaceOneNode.getResponsibleNodeIds(key).size >= 1)
      assert(bigHyperSpaceSixNodes.getResponsibleNodeIds(key).size >= 2)
    }
  }
}
