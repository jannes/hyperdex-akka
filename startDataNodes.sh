#!/usr/bin/env bash

if [ ! -d logs ]; then
  mkdir -p logs;
fi

for i in $(eval echo {1..$1}); 
do 
    echo "runMain hyperdex.ClusterTest data 200$i" > "logs/node$i.log" 2>&1  
done
