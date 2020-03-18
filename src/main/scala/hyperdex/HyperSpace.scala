package hyperdex

import hyperdex.API.{Attribute, AttributeMapping, Key}

import scala.collection.immutable.Set

class HyperSpace(attributes: Seq[String], amountNodes: Int, cutsPerAxis: Int) {

  // axis 0: key, axis 1..n: attr1 ... attrn
  case class Region(sectionPerAxis: Seq[Int])

  /**
    * find responsible nodes for a lookup
    * @param key
    * @return
    */
  def getResponsibleNodeIds(key: Key): Set[Int] =
    getPossibleRegions(Some(key), Map.empty).map(regionToNode)

  /**
    * find responsible nodes for a new item to be put
    * @param key
    * @param value
    * @return
    */
  def getResponsibleNodeIds(key: Key, value: AttributeMapping): Set[Int] =
    getPossibleRegions(Some(key), value).map(regionToNode)

  /**
    * find responsible nodes given a search query
    * @param query
    * @return
    */
  def getResponsibleNodeIds(query: AttributeMapping): Set[Int] =
    getPossibleRegions(None, query).map(regionToNode)

  val amountRegions: Attribute = cutsPerAxis ^ (attributes.size + 1)
  // ------------------------------------------------------------

  private val regionToNodeMapping: Map[Region, Int] = {
    val minAmountRegionsPerNode = amountRegions / amountNodes
    val amountNodesWithExtraRegion = amountRegions % amountNodes

    /**
      * create a random mapping from regions to nodes which is evenly distributed
      * (amounts of regions a node is responsible for differ at most by 1)
      * @return
      */
    def createRegionNodeMapping(): Map[Region, Int] = {
      // create regions by splitting each axis into sections as specified by amount of cuts
      // transform sequence of axis sections to all possible regions
      val regions = axisSectionsToRegions(
        (0 until (attributes.size + 1)).map(_ => (0 until cutsPerAxis).toSet)
      )
      // a list of responsible nodes where indices signify region ids and elements are node ids
      val responsibleNodes = (0 until amountNodes)
        .flatMap(id => {
          if (id < amountNodesWithExtraRegion)
            List.fill(minAmountRegionsPerNode + 1)(id)
          else
            List.fill(minAmountRegionsPerNode)(id)
        })
      // set of regions and seq of responsible node ids are randomly forming tuples
      regions.zip(responsibleNodes).toMap
    }

    createRegionNodeMapping()
  }

  def axisSectionsToRegions(axisSections: Seq[Set[Int]]): Set[Region] = {
    def getAllAxisSectionTails(startAxis: Int): Set[Seq[Int]] = {
      if (startAxis == axisSections.size) {
        Set.empty[Seq[Int]]
      } else {
        val sections = axisSections(startAxis)
        for {
          s <- sections
          tail <- getAllAxisSectionTails(startAxis + 1)
        } yield Seq(s) ++ tail
      }
    }

    getAllAxisSectionTails(0)
      .map(Region)
  }

  private def regionToNode(r: Region): Int = regionToNodeMapping(r)

  private def getPossibleRegions(optKey: Option[Key], attributeValues: AttributeMapping): Set[Region] = {

    def optAttributeToSection(optAtt: Option[Attribute]): Set[Int] = optAtt match {
      case Some(att) => Set(getAttributeSection(att))
      case None      => (0 until cutsPerAxis).toSet
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
