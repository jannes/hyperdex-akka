package hyperdex

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._
import scala.util.Random

class GatlingTest extends Simulation {

  def rng() = Random.nextInt(100) // Note that this only gets called once per val, not for every .exec


  val httpProtocol = http
    .baseUrl("http://localhost:8080")

  val createTable = http("post")
    .post("/create/table")
    .header("Content-Type", "application/json")
    .body(StringBody("[\"attribute1\", \"attribute2\"]"))

  val putRecord1 = http("post")
    .post(url="/put/table/1")
    .header("Content-Type", "application/json")
    .body(StringBody(s"""{ "attribute1" : ${rng()}, "attribute2" : ${rng()} }"""))

  val getTable = http("get")
    .get("/get/table/1")
    .check(status.is(200))

  val scn = scenario("BasicSimulation")
    .repeat(20){
      exec(createTable)
      .exec(putRecord1)
      .exec(getTable)
      .pause(1)
    }

  setUp(
    scn.inject(atOnceUsers(5))
  ).protocols(httpProtocol)

}
