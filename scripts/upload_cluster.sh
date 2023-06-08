#!/bin/bash

folder=$1

if [ -z "$folder" ]; then
    echo "Usage: upload_cluster.sh <folder>"
    exit 1
fi

scp -r server/deploy/ dicluster:"$folder"/server
scp -r client/deploy/ dicluster:"$folder"/client
scp -r scripts/ dicluster:"$folder"

