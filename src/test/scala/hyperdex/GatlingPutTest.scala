package hyperdex

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._
import scala.util.Random

class GatlingPutTest extends Simulation {


  val indexFeeder = Iterator.from(1).map(i => Map("index" -> i))
  val attributeFeeder = Iterator.continually(Map("attribute1" -> Random.nextInt(100).toString, "attribute2" -> Random.nextInt(100).toString))


  val httpProtocol = http
    .baseUrl("http://localhost:8080")

  val createTable = http("post")
    .post("/create/table")
    .header("Content-Type", "application/json")
    .body(StringBody("[\"attribute1\", \"attribute2\"]"))

  val putRecord1 = feed(indexFeeder).feed(attributeFeeder).exec(http("post")
    .post("/put/table/${index}")
    .header("Content-Type", "application/json")
    .body(StringBody("""{ "attribute1" : ${attribute1}, "attribute2" : ${attribute2} }""")).check(status.is(200)))

//  val getTable = http("get")
//    .get("/get/table/1")
//    .check(status.is(200))

  val repetition= 10000

  val scn = scenario(s"First ${repetition} Put")
    .exec(createTable)
    .repeat(repetition){
        exec(putRecord1)
    }
  val users = 1

  setUp(
    scn.inject(atOnceUsers(users))
  ).protocols(httpProtocol)


}



