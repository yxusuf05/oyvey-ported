#!/bin/sh
# Stand-in for Gradle in network-restricted test environments: pretends the
# build succeeded and drops a jar where the runner expects one.
mkdir -p build/libs
printf 'PK\003\004fakejar' > build/libs/testmod-1.0.0.jar
echo "BUILD SUCCESSFUL (fake)"
exit 0
