#! /usr/bin/env bash

set -eu

project_root=`pwd`

driver="$1"
driver_jar="$driver.metabase-driver.jar"

if [ ! "$driver" ]; then
    echo "Usage: ./bin/build-driver-with-test-extensions.sh [driver]"
    exit -1
fi

mkdir -p resources/modules

echo "Deleting existing $driver drivers..."
rm resources/modules/"$driver_jar" || true
rm plugins/"$driver_jar" || true

mb_jar=`find ~/.m2/repository/metabase-core/metabase-core/ -name '*.jar'`

build-test-jar() {
    echo "Building Metabase with test extensions and installing locally..."
    lein clean
    lein install-with-tests
}

if [ ! "$mb_jar" ]; then
    build-test-jar
fi

if [ ! `jar -tf "$mb_jar" | grep metabase/test/util.clj` ]; then
    echo "Metabase local dependency is missing test extensions."
    build-test-jar
fi

echo "Building $driver with test extensions..."

driver_source_dir="$project_root/modules/drivers/$driver"

cd "$driver_source_dir"

lein clean
lein with-profile +with-test-extensions uberjar

cd "$project_root"

target_jar="$driver_source_dir/target/uberjar/$driver_jar"

if [ ! -f "$target_jar" ]; then
    echo "Error: could not find $target_jar. Build failed."
    exit -1
fi

if [ `jar -tf $target_jar | grep metabase/src/api` ]; then
    echo "Error: driver JAR contains metabase-core files. Build failed."
    exit -1
fi

dest_location="$project_root/resources/modules/$driver_jar"

echo "Copying $target_jar -> $dest_location"
cp "$target_jar" "$dest_location"
