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

// O script do banco fica na pasta "banco", na raiz do projeto, pra ficar
// facil de achar. Aqui eu declaro essa pasta como resource, entao o
// schema.sql vai junto no classpath e a Migracao continua achando ele.
sourceSets {
    main {
        resources.srcDir("banco")
    }
}

application {
    mainClass.set("br.com.provedor.MainKt")
}

// Sem isso o "gradle run" nao repassa o teclado pro programa e o menu trava.
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
