# running Examples

## Cluster HttpFrontend + Receiver
in two terminals:

`sbt "runMain examples.cluster.ClusterTest hireceiver 25251"`

`sbt "runMain examples.cluster.ClusterTest hisayer 25252"`

do GET http://127.0.0.1:8080/ping to let frontend send ping to receiver

## Cluster Sender + Receiver
in two terminals:

`sbt "runMain examples.cluster.ClusterTest hireceiver 25251"`

`sbt "runMain examples.cluster.ClusterTest gateway 25252"`

sender will send pings to receiver periodically
