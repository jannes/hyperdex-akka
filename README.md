## Routes
### Create

POST /create/"table_name"

with attribute names as json list

### Lookup

GET /get/"table_name"/"key"

### Put

Post /put/"table_name"/"key"

with value specified as json dictionary

### Search

GET /search/"table_name"/"key"

with attributes values specified as json dictionary

## Cluster Sender + Receiver
in two terminals:

`sbt "runMain hyperdex.Main gateway 25251"`

`sbt "runMain hyperdex.Main data 25252"`

do GET localhost:8080/test/0 to get some example data


## Docker and `docker-compose`
Just run `docker-compose up` and wait for everything to start. You can now GET `localhost:8080/get/table/1`
NOTE: if you want to rebuild the images you have to run `docker-compose up --build` (so with build flag).

Run `docker-compose up --scale datanode=2` for running with 2 (or more) data nodes.

---

In case you need it here are the commands to build and run the docker containers separately:

First build the two containers (mind the `.` at the end):
1. `docker build -t gateway:latest -f Dockerfile.gateway .`
2. `docker build -t datanode:latest -f Dockerfile.datanode .`

Create a docker network: `docker network create --subnet=172.18.0.0/16 akka-network`

Then, in separate terminals, run them:
1. `docker run --net=akka-network --ip 172.18.0.22 -p 8080:8080 -it gateway:latest`
2. `docker run --net=akka-network --ip 172.18.0.23 -it datanode:latest`

