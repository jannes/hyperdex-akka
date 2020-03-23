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

do GET localhost:8080/get/test/0 to get some example data


## Docker and `docker-compose`
First set the `NUM_DATANODES` environment variable:
- Powershell: `$env:NUM_DATANODES=X` 
- Bash: `NUM_DATANODES=X`
 
 Do note that this variable value is not persistent (it is lost when you close the shell). If you want it to be persistent you have to export it.
Then, run: 
- Powershell: `docker-compose up --build --scale datanode=$env:NUM_DATANODES`
- Bash: `docker-compose up --build --scale datanode=$NUM_DATANODES`

You can now GET/POST as described above.
NOTE: Don't forget that if you want to rebuild the images (because you might have changed something in the source code,
for example) you have to run `docker-compose` with the `--build` flag.

---
In the rare case you need it here are the commands to build and run the docker containers separately:

First build the container (mind the `.` at the end):
1. `docker build -t hyperdex:latest -f Dockerfile .`

Create a docker network: `docker network create --subnet=172.18.0.0/16 akka-network`

Then, in separate terminals, run:
1. `docker run --net=akka-network --ip 172.18.0.22 -p 8080:8080 -it hyperdex:latest java -jar hyperdex.jar gateway 25251 1`
2. `docker run --net=akka-network --ip 172.18.0.23 -it hyperdex:latest java -jar hyperdex.jar data 25252`

