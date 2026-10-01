plugins { id("com.android.application") }

android {
    namespace="com.livetvbox.nativev1"
    compileSdk=35
    defaultConfig {
        applicationId="com.livetvbox.nativev1"
        minSdk=28
        targetSdk=35
        versionCode=3
        versionName="3.0"
    }
}
dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
}
