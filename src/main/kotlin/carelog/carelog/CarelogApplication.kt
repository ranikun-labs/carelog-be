package carelog.carelog

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CarelogApplication

fun main(args: Array<String>) {
    runApplication<CarelogApplication>(*args)
}
