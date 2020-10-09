package estudos.springcacheredis

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.serializer.GenericToStringSerializer
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import reactor.cache.CacheMono
import reactor.core.publisher.Mono
import reactor.core.publisher.Signal
import reactor.kotlin.core.publisher.toMono
import java.io.Serializable
import java.time.Duration
import java.time.temporal.ChronoUnit

@RestController
@EnableCaching
class SomeController(
        private val reactiveRedisTemplate: ReactiveRedisTemplate<Int, SomeOperation>,
        private val someService: SomeService
) {

    @GetMapping("/info/{id}")
    fun info(@PathVariable id: Int): Mono<SomeOperation> {
        return someService.info(id)
    }

    @GetMapping("/info/{id}/clear")
    fun clear(@PathVariable id: Int): Mono<ResponseEntity<String>> {
        return reactiveRedisTemplate.delete(id).map {
            ResponseEntity.ok().body("cleared")
        }
    }
}

@Service
class SomeService(
        private val reactiveRedisTemplate: ReactiveRedisTemplate<Int, SomeOperation>,
        private val cacheManager: CacheManager,
        private val someRepository: SomeRepository
) {

    fun info(id: Int): Mono<SomeOperation> {
// Uses Spring's CacheManager abstraction
//        return CacheMono.lookup( readFromCache() , id)
//                .onCacheMissResume(someRepository.info(id))
//                .andWriteWith(writeInCache())

// Uses Spring's ReactiveCacheTemplate
        return CacheMono.lookup(read(), id)
                .onCacheMissResume(someRepository.info(id))
                .andWriteWith { key, signal -> write(key, signal) }
    }

    fun write(key: Int, signal: Signal<out SomeOperation>) =
            reactiveRedisTemplate.opsForValue().set(key, signal.get()!!)
                    .flatMap { reactiveRedisTemplate.expire(key, Duration.of(30, ChronoUnit.SECONDS)) }
                    .flatMap { Mono.empty<Void>() }

    fun read() = { id: Int ->
        reactiveRedisTemplate.opsForValue().get(id)
            .map<Signal<out SomeOperation>> { value -> Signal.next(value) }
    }

    fun readFromCache() = { key: Int ->
        Mono.justOrEmpty(cacheManager.getCache("some")?.get(key, SomeOperation::class.java))
                .map<Signal<out SomeOperation>> { Signal.next(it) }
    }

    fun writeInCache() = { key: Int, signal: Signal<out SomeOperation> ->
        cacheManager.getCache("some")?.put(key, signal.get()); Mono.empty<Void>()
    }
}

@Component
class SomeRepository {
    fun info(id: Int): Mono<SomeOperation> {
        return Mono.delay(Duration.of(5, ChronoUnit.SECONDS))
                .map { SomeOperation(id) }
    }
}

data class SomeOperation @JsonCreator constructor(val id: Int) : Serializable

@Configuration
class RedisConfig {

    @Bean
    fun reactiveRedisTemplate(
            reactiveRedisConnectionFactory: ReactiveRedisConnectionFactory,
            resourceLoader: ResourceLoader,
            objectMapper: ObjectMapper
    ): ReactiveRedisTemplate<Int, SomeOperation>  {
        val keySerializer = GenericToStringSerializer(Int::class.java)
        val valueSerializer = Jackson2JsonRedisSerializer(SomeOperation::class.java).apply {
            this.setObjectMapper(objectMapper)
        }

        val redisSerializationContext = RedisSerializationContext.newSerializationContext<Int, SomeOperation>()
                .key(keySerializer)
                .value(valueSerializer)
                .hashKey(keySerializer)
                .hashValue(valueSerializer)
                .build()

        return ReactiveRedisTemplate(reactiveRedisConnectionFactory, redisSerializationContext)
    }

}