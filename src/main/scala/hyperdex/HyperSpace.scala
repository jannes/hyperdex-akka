package hyperdex

import hyperdex.API.AttributeMapping

import scala.collection.immutable.Set

/**
  * representing a Hyperspace of a Table
  * @param attributes names of the attributes (needed for query's)
  * @param bucketAmount amount of buckets used by hashing mechanism
  * @param objects of the Table
  * @param IDs array with all the unique identifiers of all the objects
  * @param attributeValuesList arrays containing the data per column
  */
class HyperSpace(attributes :Seq[String], numberOfNodes: Int) {
  var amountOfNodes = numberOfNodes;
  var bucketAmount = 100;



  //  def initHyperSpace(nodes: Int): Array[HyperSpaceNode] = {
//    amountOfNodes = nodes
//    var dimensions: Map[String, Array[Set[Int]]] = Map();
//    var hyperspaceNodes: Array[HyperSpaceNode] = new Array[HyperSpaceNode](amountOfNodes);
//
//    for (attribute <- attributes) {
//      dimensions += (attribute -> initBuckets(bucketAmount));
//    }
//
//    for (x <- 0 until 4) {
//      var attribute = attributes(x)
//      hashAttributes(attributeValuesList(x), IDs, 100, dimensions(attribute))
//    }
//
//    for (i <- hyperspaceNodes.indices){
//      var frac = i*(bucketAmount/hyperspaceNodes.length);
//      var nodes = dimensions.splitAt(frac)
//      var objectMappingPart = objectMapping.splitAt(frac)
//      hyperspaceNodes(i) = new HyperSpaceNode(nodes._1, objectMappingPart._1);
//    }
//
//    return hyperspaceNodes
//  }

  def hashAttributes(values: Array[Int], ids: Array[Int], bucketsize: Int, buckets: Array[Set[Int]])= {
    for(x <- values.indices){
      var hashValue = values(x).hashCode() % bucketsize;
      buckets(hashValue) += ids(x)
    }
  }

  def initBuckets(size: Int): Array[Set[Int]] = {
    var arr = new Array[Set[Int]](size)
    arr.map(_ => Set[Int]())
  }

  def initObjectMapping(objects: Array[AttributeMapping], ids: Array[Int]  ) ={
    for(x <- objects.indices){
      var obj = objects(x)
      var id = ids(x)
//      objectMapping += (id -> obj)
    }
  }

  def obtainindex(value: Int): Int = {
    return value.hashCode() % bucketAmount;
  }

//  def get(id: Int): Any = {
//    objectMapping(id)
//  }

  def search(query: Map[String, Int]): Map[Int,List[(String,Int)]] = {

    var coordinates: Map[Int,List[(String,Int)]] =  Map[Int,List[(String,Int)]]()

    for(filter <- query) {
      val key = filter._1
      val value = filter._2
      var valueIndex = obtainindex(value);
      var stepsize = bucketAmount / amountOfNodes;
      var nodeindex = Math.floor(valueIndex / stepsize).toInt;

      if(coordinates.contains(nodeindex)){
        var coordinate = coordinates(nodeindex)
        coordinate :+= (key, valueIndex)
      }else{
        coordinates += (nodeindex -> List((key, valueIndex)))
      }
    }

    return coordinates
  }
}

//  if(ids.isEmpty || (ids.nonEmpty && previousids.nonEmpty && ids.intersect(previousids).isEmpty)){
//        return null
//      }else{
//        if(previousids.isEmpty){
//          previousids = ids
//        }else{
//          previousids = ids.intersect(previousids)
//        }
//
//      }
//    }
//
//    var results = new Array[Any](previousids.size)
//    for(id <- previousids){
//      var obj = get(id)
//      results :+= obj
//    }

