# running Examples

## Cluster Sender + Receiver
in two terminals:

`sbt "runMain hyperdex.Main gateway 25251"`

`sbt "runMain hyperdex.Main data 25252"`

do GET http://127.0.0.1:8080/lookup to perform a dummy lookup


## Old example HttpFrontend + Receiver
in two terminals:

`sbt "runMain examples.cluster.ClusterTest hireceiver 25251"`

`sbt "runMain examples.cluster.ClusterTest hisayer 25252"`

do GET http://127.0.0.1:8080/ping to let frontend send ping to receiver

## Docker and Kubernetes
First build the two containers (mind the `.` at the end):
1. `docker build -t gateway:latest -f Dockerfile.gateway .`
2. `docker build -t datanode:latest -f Dockerfile.datanode .`


Then, in separate terminals, run them:
1. `docker run -p 8080:8080 -p 25251:25251 -it gateway:latest`
2. `docker run -p 25252:25252 -it datanode:latest`
