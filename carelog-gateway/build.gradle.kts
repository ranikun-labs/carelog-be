plugins {
    id("org.springframework.boot")           // 버전은 루트 build.gradle에서 관리
    id("io.spring.dependency-management")
    kotlin("jvm")                            // Kotlin JVM 플러그인
    kotlin("plugin.spring")                  // @Component 등 Spring 어노테이션을 Kotlin에서 쓰기 위해 필요
}

kotlin {
    jvmToolchain(17)                         // Java 17 기준으로 컴파일
}

dependencyManagement {
    imports {
        // Spring Cloud 버전 관리 — Spring Boot 3.4.x와 호환되는 버전
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2024.0.1")
    }
}

dependencies {
    // SCG 본체 — Reactive(WebFlux) 기반이라 일반 spring-boot-starter-web과 같이 쓰면 충돌남
    implementation("org.springframework.cloud:spring-cloud-starter-gateway")

    // Redis — SCG는 Reactive라서 reactive 버전 사용
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

    // JWT 검증용
    implementation("io.jsonwebtoken:jjwt-api:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.3")

    // Kotlin + Jackson 연동 (JSON 직렬화)
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}