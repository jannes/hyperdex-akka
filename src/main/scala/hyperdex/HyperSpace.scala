package hyperdex

import hyperdex.API.{Attribute, AttributeMapping, Key}

import scala.collection.immutable.Set

class HyperSpace(attributes: Seq[String], amountNodes: Int, cutsPerAxis: Int) {

  case class Region(sectionPerAxis: Seq[Int])

  // axis 0: key, axis 1..n: attr1 ... attrn

  private val amountRegions = cutsPerAxis ^ (attributes.size + 1)
  private val regionToNodeMapping: Map[Region, Int] = ???

  def getResponsibleNodeIds(key: Key): Set[Int] =
    getPossibleRegions(Some(key), Map.empty).map(regionToNode)

  def getResponsibleNodeIds(key: Key, query: AttributeMapping): Set[Int] =
    getPossibleRegions(Some(key), query).map(regionToNode)

  def getResponsibleNodeIds(query: AttributeMapping): Set[Int] =
    getPossibleRegions(None, query).map(regionToNode)

  private def regionToNode(r: Region): Int = regionToNodeMapping(r)

  private def getPossibleRegions(optKey: Option[Key], attributeValues: AttributeMapping): Set[Region] = {

    def optAttributeToSection(optAtt: Option[Attribute]): Set[Int] = optAtt match {
      case Some(att) => Set(getAttributeSection(att))
      case None      => (0 until cutsPerAxis).toSet
    }

    def axisSectionsToRegions(axisSections: Seq[Set[Int]]): Set[Region] = {
      def getAllAxisSectionTails(startAxis: Int): Set[Seq[Int]] = {
        if (startAxis == axisSections.size) {
          Set.empty[Seq[Int]]
        } else {
          val sections = axisSections(startAxis)
          for {
            s <- sections
            tail <- getAllAxisSectionTails(axisSections, startAxis + 1)
          } yield Seq(s) ++ tail
        }
      }

      getAllAxisSectionTails(0)
        .map(Region)
    }

    val keyAxisSections = optAttributeToSection(optKey)
    val attributesSections = attributes
      .map(a => optAttributeToSection(attributeValues.get(a)))
    val axisSections = Seq(keyAxisSections) ++ attributesSections
    axisSectionsToRegions(axisSections)
  }

  private def getAttributeSection(attributeValue: Attribute): Int = {
    attributeValue % cutsPerAxis
  }

}
