#!/bin/sh
# Stand-in for a failing Gradle build: emits a realistic javac error.
cat >&2 <<'EOF'
> Task :compileJava FAILED
/work/src/main/java/com/modforge/testmod/ModItems.java:14: error: cannot find symbol
        Registry.register(BuiltInRegistries.ITEMS, key, item);
                                           ^
  symbol:   variable ITEMS
  location: class BuiltInRegistries
1 error
EOF
echo "BUILD FAILED"
exit 1
