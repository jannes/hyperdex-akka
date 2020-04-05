# amounts=(1,2,4,8,16)
amounts=(1)
for ((i = 0 ; i < ${#amounts[@]}; i++)); do
    amount=${amounts[i]}
    export NUM_DATANODES=$amount 
    echo $amount
    docker-compose up --scale datanode=$NUM_DATANODES -d
    sleep 10
    sbt "gatling:testOnly hyperdex.Experiment2" 
    docker-compose down
    mkdir "experiment2-${amount}n"
    cp -r target/gatling/experiment2*/* "experiment2-{$amount}n"
    rm -rf target/gatling/experiment2*
done
