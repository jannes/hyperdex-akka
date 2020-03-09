package hyperdex

import java.util.UUID

object HyperSpaceCreator {
  class TestObject(attr1: Int, attr2:Int, attr3:Int, attr4:Int){
    var ID: UUID = new UUID(attr1,attr2);
    var attribute1: Int = attr1
    var attribute2: Int = attr2
    var attribute3: Int = attr3
    var attribute4: Int = attr4
  }
  val bucketSize = 100;

  def generateTestObjects(amount: Int): Array[TestObject] ={
    var array = new Array[TestObject](amount);
    val r = scala.util.Random

    for(a <- 0 until amount){
      var testobj = new TestObject(r.nextInt(100), r.nextInt(20000), r.nextInt(3000), r.nextInt(10))
      array +:= testobj
    }
    return array
  }



  def main(args: Array[String]): Unit = {
    val objects = generateTestObjects(10000).filter(x => x != null);
    var IDs: Array[UUID] = objects.map( x => x.ID );
    var attr1list: Array[Int] = objects.map( x => x.attribute1 );
    var attr2list: Array[Int] = objects.map( x => x.attribute2 );
    var attr3list: Array[Int] = objects.map( x => x.attribute3 );
    var attr4list: Array[Int] = objects.map( x => x.attribute4 );
    var attributeValuesList: Array[Array[Int]]= Array(attr1list,attr2list, attr3list, attr4list);
    var attributeslist = List("attribute1","attribute2", "attribute3", "attribute4");
    var hyperSpace = new HyperSpace(attributeslist, 100, objects, IDs, attributeValuesList);


    var res1 = hyperSpace.search(Array("attribute1=40"))
    var res2 = hyperSpace.search(Array("attribute4=2"))
    var res3 = hyperSpace.search(Array("attribute4=2","attribute1=30"))

  }



}
