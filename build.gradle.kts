plugins {
    java
    id("io.freefair.lombok") version "5.3.0"
}

group = "org.tribot"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.9.0")
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("org.java-websocket:Java-WebSocket:1.5.3")
    implementation("com.jcraft:jsch:0.1.55")

    testImplementation("org.slf4j:slf4j-simple:1.7.36")
    testImplementation("junit:junit:4.13.2")
}