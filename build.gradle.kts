plugins {
    kotlin("jvm") version "2.1.0"
    application
}

group = "br.com.provedor"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.postgresql:postgresql:42.7.4")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("br.com.provedor.MainKt")
}

// Sem isso o "gradle run" nao repassa o teclado pro programa e o menu trava.
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
