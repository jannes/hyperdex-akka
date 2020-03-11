# running Examples

## Cluster Sender + Receiver
in two terminals:

`sbt "runMain hyperdex.ClusterTest gateway 25251"`

`sbt "runMain hyperdex.ClusterTest data 25252"`

do GET http://127.0.0.1:8080/lookup to perform a dummy lookup


## Old example HttpFrontend + Receiver
in two terminals:

`sbt "runMain examples.cluster.ClusterTest hireceiver 25251"`

`sbt "runMain examples.cluster.ClusterTest hisayer 25252"`

do GET http://127.0.0.1:8080/ping to let frontend send ping to receiver