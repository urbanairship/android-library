# Airship Android SDK 15.x to 16.0 Migration Guide

## Compile and Target SDK Versions

Urban Airship now requires `compileSdk` version 31 (Android 12) or higher.

Please update the `build.gradle` file:

```groovy
android {
    compileSdkVersion 31

    defaultConfig {
        minSdkVersion 21
        targetSdkVersion 31

        // ...
    }
}
```

## Java 8 Source Compatibility

Urban Airship now requires Java 8 language features across all SDK modules.

Please update Android Gradle Plugin to version `3.0.0` or higher and change the source and target
compatibility for each module that uses Airship SDKs:

```groovy
android {
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
}
```

Modules using Kotlin will also need to set the target version of the generated JVM bytecode:

```groovy
tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).configureEach  {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8
    }
}
```
