plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("org.json:json:20260814")
    testImplementation(libs.junit)
}
