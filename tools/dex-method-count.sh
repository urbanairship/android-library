#!/bin/bash

dir=$(mktemp -d -t dex)
${1} --dex --output=$dir/temp.dex ${2}
cat $dir/temp.dex | head -c 92 | tail -c 4 | hexdump -e '1/4 "%d\n"'

