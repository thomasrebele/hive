#!/bin/bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


# The maven-shade-plugin can take a long time to resolve the tests jars
# This script gets a list of <include> attributes to configure the maven-shade-plugin.

JAR_FILE="$1"

if [ "$JAR_FILE" == "" ]; then
  echo "usage: </path/to/a-hive-tests.jar>"
  exit 1
fi


for POM_PROPERTIES_PATH in $(unzip -l "$JAR_FILE" | grep 'pom.properties' | awk '{print $4}'); do
  unzip -qq -c "$JAR_FILE" "$POM_PROPERTIES_PATH" |
    grep -v -e "^$" -e "^#" |
    sort |
    tr '\n' ' '  |
    sed 's#artifactId=\([^ ]*\) groupId=\([^ ]*\) version=\([^ ]*\) *#<include>\2:\1:*:jar:tests</include>#'
  echo
done | sort

