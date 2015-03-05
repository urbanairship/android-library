#!/bin/bash
# ./set-android-icons.sh <png file> <PushSample or RichPushSample>

p=`dirname "$0"`
f="${p}/../samples/${2}/res"
echo ${f}

sips --resampleWidth 48 "${1}" --out "${f}/mipmap-mdpi/ic_launcher.png"
sips --resampleWidth 72 "${1}" --out "${f}/mipmap-hdpi/ic_launcher.png"
sips --resampleWidth 96 "${1}" --out "${f}/mipmap-xhdpi/ic_launcher.png"
sips --resampleWidth 144 "${1}" --out "${f}/mipmap-xxhdpi/ic_launcher.png"
sips --resampleWidth 192 "${1}" --out "${f}/mipmap-xxxhdpi/ic_launcher.png"
