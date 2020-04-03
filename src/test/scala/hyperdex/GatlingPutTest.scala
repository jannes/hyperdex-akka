package hyperdex

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._
import scala.util.Random

object PutInfo{
  var indexid = 0
}

class GatlingPutTest extends Simulation {


  val indexFeeder = Iterator.from(1).map(i => Map("index" -> i))

  val indexFeeder2 = Iterator.from(101).map(i => Map("index" -> i))
  val indexFeeder3 = Iterator.from(201).map(i => Map("index" -> i))
  val indexFeeder4 = Iterator.from(301).map(i => Map("index" -> i))
  val indexFeeder5 = Iterator.from(401).map(i => Map("index" -> i))
  val indexFeeder6 = Iterator.from(501).map(i => Map("index" -> i))
  val attributeFeeder = Iterator.continually(Map("attribute1" -> Random.nextInt(100).toString, "attribute2" -> Random.nextInt(100).toString))


  val httpProtocol = http
    .baseUrl("http://localhost:8080")

  val createTable = http("post")
    .post("/create/table")
    .header("Content-Type", "application/json")
    .body(StringBody("[\"attribute1\", \"attribute2\"]"))

  val putRecord1 = http("post")
    .post(url="/put/table/${index}")
    .header("Content-Type", "application/json")
    .body(StringBody("""{ "attribute1" : ${attribute1}, "attribute2" : ${attribute2} }""")).check(status.is(200))

//  val getTable = http("get")
//    .get("/get/table/1")
//    .check(status.is(200))

  val repetition= 100

  val scn = scenario("First 100 Put")
    .exec(createTable)
    .repeat(repetition){
      feed(indexFeeder)
        .feed(attributeFeeder)
        .exec(putRecord1)
        .pause(1)
    }

  val scn2 = scenario("After 100 Put")
    .repeat(repetition){
      feed(indexFeeder2)
        .feed(attributeFeeder)
        .exec(putRecord1)
        .pause(1)
    }

  val scn3 = scenario("After 200 Put")
    .repeat(repetition){
      feed(indexFeeder3)
        .feed(attributeFeeder)
        .exec(putRecord1)
        .pause(1)
    }

  val scn4 = scenario("After 300 Put")
    .repeat(repetition){
      feed(indexFeeder4)
        .feed(attributeFeeder)
        .exec(putRecord1)
        .pause(1)
    }

  val scn5 = scenario("After 400 Put")
    .repeat(repetition){
      feed(indexFeeder5)
        .feed(attributeFeeder)
        .exec(putRecord1)
        .pause(1)
    }



  setUp(
    scn.inject(atOnceUsers(1)),
    scn2.inject(atOnceUsers(1)),
    scn3.inject(atOnceUsers(1)),
    scn4.inject(atOnceUsers(1)),
    scn5.inject(atOnceUsers(1))

  ).protocols(httpProtocol)


}



