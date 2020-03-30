package hyperdex

import io.gatling.core.Predef._
import io.gatling.core.body.StringBody
import io.gatling.http.Predef._


class Experiment2 extends Simulation {

  def generateTableString(numAttributes: Int): StringBody = {
    if(numAttributes == 1)
      return StringBody("[\"attribute1\"]")

    var generateTableString: String = "[\"attribute1\""
    for(attribute <- 2 to numAttributes){
      generateTableString = generateTableString.concat(s""", "attribute$attribute"""")
    }
    generateTableString = generateTableString.concat("]")
    StringBody(generateTableString)
  }

  def generatePutString(numAttributes: Int): StringBody = {
    if(numAttributes == 1)
      return StringBody(s"""{ "attribute1" : ${1}"""")

    var putString: String = s"""{"attribute1" : ${1}"""
    for(attribute <- 2 to numAttributes){
       putString = putString.concat(s""", "attribute$attribute" : ${1}""")
    }
    putString = putString.concat("}")

    StringBody(putString)
  }



  val httpProtocol = http
    .baseUrl("http://localhost:8080")

  val createTable = http("createTable")
    .post("/create/table")
    .header("Content-Type", "application/json")
    .body(generateTableString(8))
    .check(bodyString is "Create successful")

  val putRecord = http("putRecord")
    .post(url="/put/table/${n}") //n is provided by loop in the scenario
    .header("Content-Type", "application/json")
    .body(generatePutString(8))
    .check(bodyString is "Put Succeeded")



  val scn = scenario("SearchSimulation")
    .exec(createTable)
    .repeat(100, "n"){
      exec(putRecord)
    }


  setUp(
    scn.inject(atOnceUsers(1))
  ).protocols(httpProtocol)

}
