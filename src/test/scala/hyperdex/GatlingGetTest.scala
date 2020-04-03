package hyperdex

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._
import scala.util.Random

class GatlingGetTest extends Simulation {

  val repetition= 1000

  val randomIndexFeeder = Iterator.continually(Map("randomIndex" -> Random.nextInt(repetition).toString))
  val indexFeeder = Iterator.from(1).map(i => Map("index" -> i))
  val attributeFeeder = Iterator.continually(Map("attribute1" -> Random.nextInt(100).toString, "attribute2" -> Random.nextInt(100).toString))

  val putRecord = feed(indexFeeder).feed(attributeFeeder).exec(http("post")
    .post("/put/table/${index}")
    .header("Content-Type", "application/json")
    .body(StringBody("""{ "attribute1" : ${attribute1}, "attribute2" : ${attribute2} }""")).check(status.is(200)))


  val httpProtocol = http
    .baseUrl("http://localhost:8080")

  val createTable = http("post")
    .post("/create/table")
    .header("Content-Type", "application/json")
    .body(StringBody("[\"attribute1\", \"attribute2\"]"))

    val getTable = http("get")
     .get("/get/table/${randomIndex}")
    .check(status.is(200)).check(bodyString.exists)

  val scn = scenario(s"Test get after ${repetition} put")
    .exec(createTable)
    .repeat(repetition){
      exec(putRecord)
    }.repeat(repetition/2){
    feed(randomIndexFeeder)
    .exec(getTable)
  }

  val scn2 = scenario(s"Test get after ${repetition*2} put")
    .repeat(repetition){
        exec(putRecord)
    }.repeat(repetition/2){
    feed(randomIndexFeeder)
      .exec(getTable)
  }
  setUp(
    scn.inject(atOnceUsers(1)),
    scn2.inject(atOnceUsers(1))

  ).protocols(httpProtocol)


}