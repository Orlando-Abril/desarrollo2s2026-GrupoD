# Resultados de rendimiento: detalle de jugador

**Fecha**: 2026-10-27  
**Entorno**: Spring Boot Test, MockMvc, H2 2.4.x, Java 22  
**Comando**: `./mvnw.cmd "-Dtest=PlayerDetailPerformanceIT" test`

## Protocolo

- Catálogo local: 2.500 jugadores.
- Jugador medido: con estadísticas y cotización de la estrategia activa.
- Fuentes externas: reemplazadas por mocks, sin habilitarlas ni simularlas.
- Warm-up: 5 pedidos autenticados no medidos.
- Medición: 100 pedidos autenticados secuenciales a `GET /players/{id}`.
- Umbral: al menos 95 respuestas por debajo de 1.000 ms.

## Duraciones

Milisegundos, en orden de ejecución:

```text
28, 27, 27, 27, 25, 25, 25, 24, 18, 18,
20, 17, 19, 19, 18, 21, 27, 27, 20, 16,
19, 22, 18, 24, 22, 14, 20, 15, 14, 17,
16, 15, 14, 14, 13, 15, 16, 18, 14, 13,
13, 16, 15, 15, 15, 19, 18, 14, 14, 18,
16, 16, 14, 14, 15, 17, 18, 17, 25, 17,
15, 16, 13, 16, 15, 14, 14, 18, 19, 16,
16, 18, 17, 15, 16, 33, 18, 18, 20, 17,
22, 16, 15, 16, 15, 16, 15, 17, 18, 17,
15, 16, 17, 15, 17, 14, 15, 14, 15, 14
```

## Resultado

- Mínimo: 13 ms.
- Máximo: 33 ms.
- Promedio: 17,70 ms.
- Bajo 1 segundo: 100/100 (100%).
- Criterio de al menos 95/100: **PASS**.
- Build de la prueba: **SUCCESS**.
