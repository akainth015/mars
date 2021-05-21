plugins {
    application
    java
}

repositories {
    mavenCentral()
}

application {
    mainModule.set("mars.main")
    mainClass.set("mars.Mars")
    version = "4.6"
}
