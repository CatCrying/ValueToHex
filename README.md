# ValueToHex
[![Build Debug APK](https://github.com/CatCrying/ValueToHex/actions/workflows/build_debug.yml/badge.svg)](https://github.com/CatCrying/ValueToHex/actions/workflows/build_debug.yml)

[![License Info](https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=flat-square)](https://github.com/CatCrying/ValueToHex/blob/main/LICENSE)


![](img1.jpg)

## Download
<!-- [<img src="./source/coolapk-badge.png" width="323" height="125" />](https://www.coolapk.com/apk/com.absinthe.libchecker) -->

Original project: https://github.com/FlutterGenerator/ValueToHex (GPL-3.0)

## Development

The encoding logic lives in `PatchEncoder` (pure Java, no Android dependencies) and is covered by unit tests,
including a golden file of 1,234 cases generated from the reference web tool
(https://valuetohex-tsup.vercel.app), a fuzz test and range-limit tests.

```bash
./gradlew testDebugUnitTest   # run unit tests
./gradlew assembleDebug       # build app/build/outputs/apk/debug/app-debug.apk
```

