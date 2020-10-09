# Exemplo de uso de Cache do Spring com Redis e Webflux

Existe um addon do Reactor que permite trabalhar com caches, ele adiciona as classes CacheMono e CacheFlux.

Tem duas implementações disponíveis:
- Usando ReactiveRedisTemplate
- Usando CacheManager (Não reativo)

Existe um docker-compose no projeto para subir um cluster de redis

