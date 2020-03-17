package hyperdex

import java.util.UUID

import hyperdex.API.AttributeMapping

import scala.collection.mutable.Set

class HyperSpaceNode (dimensionsMap: Map[String, Array[Set[Int]]], objectMapping : Map[Int, AttributeMapping]) {
  var dimensions: Map[String, Array[Set[Int]]] = dimensionsMap;
  var objects: Map[Int, AttributeMapping] = objectMapping;

  def search(attribute: String, valueIndex: Int): Set[Int] = {
    var attributeMap: Array[Set[Int]] = dimensions.get(attribute).get
    attributeMap(valueIndex)
  }
}
