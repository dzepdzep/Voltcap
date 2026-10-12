#!/bin/bash
# Real type-check of src_copy (minus ThemeShaders.kt) against the project's libraries. Usage: check.sh SRC_DIR
SRC=${1:-/tmp/claude-0/w/src}
CC=/tmp/claude-0/cc
CP=$(ls $CC/cp/*.jar | tr '\n' ':')$CC/themeshaders.jar:$CC/R.jar:$CC/android.jar
find $SRC -name "*.kt" ! -name ThemeShaders.kt > $CC/srcs.txt
python3 $CC/genR.py $SRC $CC/rgen/com/shelf/launcher/R.java && rm -rf $CC/rcls && mkdir -p $CC/rcls && javac -nowarn -d $CC/rcls $CC/rgen/com/shelf/launcher/R.java 2>/dev/null && (cd $CC/rcls && rm -f ../R.jar && zip -q -r ../R.jar .)
rm -rf $CC/out
K=/tmp/claude-0/kotlin/kotlinc
JAVA_OPTS="-Xmx5g -Xss8m" $K/bin/kotlinc @$CC/srcs.txt -d $CC/out -jvm-target 17 -no-stdlib -no-reflect \
  -classpath "$CP" -Xplugin=$K/lib/compose-compiler-plugin.jar -nowarn \
  -Xskip-prerelease-check -module-name app_debug -Xfriend-paths=$CC/themeshaders.jar 2>&1 | grep -v "^Picked up" > $CC/log.txt
grep -c "error:" $CC/log.txt
