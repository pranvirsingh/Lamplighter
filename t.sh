#!/bin/bash
# compile core+tests and run a main class
cd /home/claude/lamplighter
rm -rf build/jvm
./kc.sh build/jvm "" src/com/pranvir/lamplighter/{Core,Chars,Game,Minis,Scenes,Synth}.kt jvmtest/shim/Graphics.kt jvmtest/*.kt 2>&1 | grep -E "error" | head -20
for m in "$@"; do java -Djava.awt.headless=true -cp build/jvm:/home/claude/tc/kotlin-stdlib-2.3.10-RC.jar $m 2>&1 | grep -v JAVA_TOOL | tail -25; done
