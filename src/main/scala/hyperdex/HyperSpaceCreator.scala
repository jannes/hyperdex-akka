package hyperdex

import hyperdex.API.{Attribute, AttributeMapping}

object HyperSpaceCreator {
  class TestObject(Id: Int, attr1: Int, attr2:Int, attr3:Int, attr4:Int){
    var ID: Int = Id;
    var attribute1: Int = attr1
    var attribute2: Int = attr2
    var attribute3: Int = attr3
    var attribute4: Int = attr4
  }
  val bucketSize = 100;
  var hyperspaceNodes: Array[HyperSpaceNode] = null;
  var hyperspace: HyperSpace = null;

  def generateTestObjects(amount: Int): Array[AttributeMapping] ={
    var array = new Array[AttributeMapping](amount);
    val r = scala.util.Random

    for(a <- 0 until amount){
      var testobj = Map[String, Attribute] ("Id" ->a, "attribute1" -> r.nextInt(100), "attribute2" -> r.nextInt(20000),"attribute3" ->  r.nextInt(3000), "attribute4" -> r.nextInt(10))
      array +:= testobj
    }
    return array
  }

  def initHyperspace(hyperSpace: HyperSpace, nodes: Int) : Array[HyperSpaceNode] = {
    hyperspace = hyperSpace
    hyperspaceNodes = hyperSpace.initHyperSpace(nodes)
    return hyperspaceNodes
  }

  def search(query: Array[String]) = {
   // var coordinates: Map[Int,List[(String,Int)]] = hyperspace.search(query);

    //for(server <- coordinates){
      //var serverRef = getReference(server)
      //sendMessage(serverRef, message)
  //  }
  }


  def initTestHyperSpace(): HyperSpace = {
    val objects: Array[AttributeMapping] = generateTestObjects(10000).filter(x => x != null);
    var IDs: Array[Int] = objects.map( x => x("Id") );
    var attr1list: Array[Int] = objects.map( x => x("attribute1") );
    var attr2list: Array[Int] = objects.map( x => x("attribute2") );
    var attr3list: Array[Int] = objects.map( x => x("attribute3") );
    var attr4list: Array[Int] = objects.map( x => x("attribute4") );
    var attributeValuesList: Array[Array[Int]]= Array(attr1list,attr2list, attr3list, attr4list);
    var attributeslist = List("attribute1","attribute2", "attribute3", "attribute4");
    hyperspace = new HyperSpace(attributeslist, 100, objects, IDs, attributeValuesList);
    return hyperspace
  }



}


