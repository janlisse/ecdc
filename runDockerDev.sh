#!/usr/bin/env bash

docker run -t -i --rm -p 9000:9000 -w /var/app \
       --name ecdc \
       -v ~/.m2:/root/.m2:rw \
       -v ~/.ivy2:/root/.ivy2:rw \
       -v $(pwd):/var/app janlisse/java-8-server:latest /bin/sh