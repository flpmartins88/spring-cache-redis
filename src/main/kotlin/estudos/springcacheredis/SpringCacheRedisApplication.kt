package estudos.springcacheredis

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SpringCacheRedisApplication

fun main(args: Array<String>) {
    runApplication<SpringCacheRedisApplication>(*args)
}
