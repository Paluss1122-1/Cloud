# Gradle Build Policy

## DO NOT RUN GRADLE BUILDS

**Never execute any Gradle build, sync, assemble, bundle, install, lint, test, or similar Gradle task unless the user explicitly requests it.**

This includes (but is not limited to):

* `./gradlew build`
* `./gradlew assemble`
* `./gradlew assembleDebug`
* `./gradlew assembleRelease`
* `./gradlew bundle`
* `./gradlew install*`
* `./gradlew lint`
* `./gradlew test`
* `./gradlew connectedAndroidTest`
* Android Studio "Make Project"
* Android Studio "Build Project"
* Any automatic validation build
* Any background build to "verify" changes

When making code changes:

* Modify the source files only.
* Do **not** validate the changes by running Gradle.
* Do **not** claim that the project compiles unless the user has explicitly requested and allowed a build.
* If build validation would normally be required, simply state that it was not performed because Gradle execution is prohibited by this policy.

This restriction exists because Gradle builds are time-consuming and are only performed manually by the project owner.
