# Contract: E2EFixtures

## Scope

Contrato interno de soporte para esta feature y las features 017, 018 y 021. No es una API productiva ni crea nuevos endpoints.

## Location and visibility

- Archivo: `backend/src/test/java/com/example/demo/support/E2EFixtures.java`
- Paquete: `com.example.demo.support`
- Visibilidad: pública para consumo desde paquetes e2e futuros.
- Dependencia interna: una instancia real de `PlayerRepository` inyectada por constructor y encapsulada por el helper.
- Registro Spring: `@Component` definido en `src/test`; no forma parte del artefacto productivo.
- Dependencia del consumidor: únicamente `E2EFixtures`; ninguna prueba consumidora inyecta ni recibe `PlayerRepository`.

## Injection contract

```text
@Autowired E2EFixtures fixtures
```

- Spring construye el componente con `PlayerRepository`; ese constructor es un detalle interno, no la API de consumo.
- Una dependencia de repositorio ausente impide crear el contexto y falla inmediatamente con diagnóstico claro.
- No retiene entidades de invocaciones anteriores.
- No limpia la base; la limpieza pertenece a `@E2ETest`.
- Las features 017, 018 y 021 pueden inyectar el helper sin conocer operaciones o tipos de persistencia.

## Creation operation

```text
FixtureSet createPlayersFromFiveLeagues()
```

Postconditions:

1. Persiste y fuerza el flush de exactamente cinco jugadores canónicos.
2. Cada valor de `League` aparece exactamente una vez.
3. Todos poseen `externalId`, nombre, equipo, posiciones y `marketValue` válidos.
4. Devuelve las entidades persistidas con sus IDs generados.
5. No crea usuarios, datos de caché, tokens, cotizaciones ni transacciones.
6. No promete idempotencia dentro de un mismo método; el consumidor lo llama una vez después de la limpieza.

## FixtureSet operations

```text
List<Player> all()
Player forLeague(League league)
Player forTeam(String team)
List<Player> withPosition(Position position)
```

- Las listas retornadas son inmutables.
- `forLeague` y `forTeam` retornan una coincidencia única o fallan con un mensaje que identifica el criterio ausente/duplicado.
- `withPosition` incluye toda entidad cuyo conjunto contenga la posición; no modifica las entidades.
- Ninguna búsqueda ejecuta queries adicionales: opera sobre el set retornado.

## Stability contract

Son estables entre features:

- Los cinco `externalId`.
- La relación liga/equipo/posición definida en [data-model.md](../data-model.md).
- La cobertura de las cinco ligas y cuatro posiciones.
- La combinación única `PREMIER_LEAGUE` + `Arsenal FC` + `FORWARD`.

No son estables y no deben hardcodearse fuera del helper:

- IDs generados por PostgreSQL.
- Orden de persistencia o de lectura desde el endpoint.
- Representación interna de Hibernate/JPA.

## Failure behavior

- Una falla de persistencia se propaga al test; no se ignora ni genera un set parcial.
- Un criterio de búsqueda ausente o ambiguo falla inmediatamente; no retorna `null`.
- El helper no captura errores para convertirlos en skips.

## Non-goals

- Poblar Redis.
- Simular Football-Data o WhoScored.
- Crear escenarios financieros.
- Reemplazar factories unitarias específicas de otras capas.
