#!/bin/bash
# ./set-android-icons.sh <png file> <PushSample or RichPushSample>

p=`dirname "$0"`
f="${p}/../samples/${2}/res"
echo ${f}

sips --resampleWidth 48 "${1}" --out "${f}/drawable-mdpi/ic_launcher.png"
sips --resampleWidth 72 "${1}" --out "${f}/drawable-hdpi/ic_launcher.png"
sips --resampleWidth 96 "${1}" --out "${f}/drawable-xhdpi/ic_launcher.png"
