plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

sourceSets.main {
    java.srcDirs("src/main/kotlin")
}

dependencies {
    api(libs.javax.inject)
    implementation(kotlin("stdlib"))
    implementation(project(":core"))
    implementation("com.google.devtools.ksp:symbol-processing-api:1.9.0-1.0.13")
}