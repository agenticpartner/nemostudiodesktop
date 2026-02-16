#!/usr/bin/env sh
# Run Nemo Studio (uses Java version from .java-version and Maven Wrapper)
cd "$(dirname "$0")"
[ -f ./set-java.sh ] && . ./set-java.sh
./mvnw javafx:run
