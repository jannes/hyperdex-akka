package hyperdex

import scala.collection.immutable.Set
import java.util.UUID

/**
  * representing a Hyperspace of a Table
  * @param attributes names of the attributes (needed for query's)
  * @param bucketSize amount of buckets used by hashing mechanism
  * @param objects of the Table
  * @param IDs array with all the unique identifiers of all the objects
  * @param attributeValuesList arrays containing the data per column
  */
class HyperSpace(attributes :List[String], bucketSize: Int, objects: Array[_], IDs: Array[UUID], attributeValuesList: Array[Array[Int]] ) {
  var dimensions: Map[String, Array[Set[UUID]]] = Map();
  var objectMapping : Map[UUID,_] = Map()
  initObjectMapping(objects, IDs)
  initHyperSpace()


  def initHyperSpace(): Unit = {
    for (attribute <- attributes) {
      dimensions += (attribute -> initBuckets(bucketSize));
    }

    for (x <- 0 until 4) {
      var attribute = attributes(x)
      hashAttributes(attributeValuesList(x), IDs, 100, dimensions(attribute))
    }
  }

  def hashAttributes(values: Array[Int], ids: Array[UUID], bucketsize: Int, buckets: Array[Set[UUID]])= {
    for(x <- values.indices){
      var hashValue = values(x).hashCode() % bucketsize;
      buckets(hashValue) += ids(x)
    }
  }

  def initBuckets(size: Int): Array[Set[UUID]] = {
    var arr = new Array[Set[UUID]](size)
    arr.map(_ => Set[UUID]())
  }

  def initObjectMapping(objects: Array[_], ids: Array[UUID]  ) ={
    for(x <- objects.indices){
      var obj = objects(x)
      var id = ids(x)
      objectMapping += (id -> obj)
    }
  }

  def obtainresults(key: String, value: String): Set[UUID] = {
    var attribute = dimensions(key)
    return attribute(value.toInt.hashCode() % bucketSize )
  }

  def get(id: UUID): Any = {
    objectMapping(id)
  }

  def search(query: Array[String]): Array[_] = {

    var previousids: Set[UUID] = Set[UUID]()

    for(filter <- query){
      var keyandvalue = filter.split("=")
      var ids = obtainresults(keyandvalue(0),keyandvalue(1))
      if(ids.isEmpty || (ids.nonEmpty && previousids.nonEmpty && ids.intersect(previousids).isEmpty)){
        return null
      }else{
        if(previousids.isEmpty){
          previousids = ids
        }else{
          previousids = ids.intersect(previousids)
        }

      }
    }

    var results = new Array[Any](previousids.size)
    for(id <- previousids){
      var obj = get(id)
      results :+= obj
    }

    return results
  }
}

