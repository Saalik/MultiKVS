#!/bin/bash

# start transaction
curl -H "Content-Type: application/json" -X GET http://127.0.0.1:8080/begin

# put

for i in {1..100}
do
  json_data="{\"id\":0, \"key\":\"hello$i\", \"value\":\"hello123\"}"
  echo "$json_data"
  curl -H "Content-Type: application/json" -X PUT -d "$json_data" http://localhost:8080/put
done

