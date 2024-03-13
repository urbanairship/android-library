#!/bin/bash -ex
# ./generate-android-icons.sh <png file> <path>

p=`dirname "$2"`

sips --resampleWidth 72 "${1}" --out "${2}/mipmap-hdpi/ic_launcher.png"
sips --resampleWidth 96 "${1}" --out "${2}/mipmap-xhdpi/ic_launcher.png"
sips --resampleWidth 144 "${1}" --out "${2}/mipmap-xxhdpi/ic_launcher.png"
sips --resampleWidth 192 "${1}" --out "${2}/mipmap-xxxhdpi/ic_launcher.png"
