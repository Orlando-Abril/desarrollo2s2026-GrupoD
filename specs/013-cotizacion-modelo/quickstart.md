# Quickstart: validar el modelo de cotización

**Feature**: `specs/013-cotizacion-modelo`

## Requisitos previos

- JDK 17 y el wrapper de Maven del proyecto (`backend/mvnw`).
- Para el escenario e2e: PostgreSQL y Redis locales, con las mismas variables que usa la feature 010 (`E2E_DB_URL`, `E2E_DB_USER`, `E2E_DB_PASSWORD`, `REDIS_HOST` y `REDIS_PORT`).

Todos los comandos se corren desde `backend/`.

## 1. Catálogo y definiciones (sin contexto de Spring)

```bash
./mvnw test -Dtest='ValuationStrategyCatalogTest,ValuationStrategyDefinitionTest,OfficialStrategyV1Test,MomentumStrategyV1Test'
```

Resultado esperado:

- `all()` devuelve OFFICIAL v1 y MOMENTUM v1.
- `find("XYZ")` falla y el mensaje nombra los códigos válidos.
- Los pesos coinciden con [contracts/valuation-strategies.md](contracts/valuation-strategies.md).
- Una definición inválida, como una posición faltante o `maxVariation > 1`, se rechaza.

## 2. Estrategia activa

```bash
./mvnw test -Dtest='ValuationStrategyActivePropertyTest'
```

Resultado esperado:

- Sin la propiedad, la activa es OFFICIAL.
- Con `valuation.active-strategy=MOMENTUM`, la activa es MOMENTUM y `all()` sigue devolviendo las dos.
- Con `valuation.active-strategy=XYZ`, el contexto no arranca y el mensaje nombra `XYZ` y los códigos válidos.

Prueba manual opcional: correr `VALUATION_ACTIVE_STRATEGY=XYZ ./mvnw spring-boot:run`. La aplicación tiene que fallar al arrancar con ese mismo mensaje.

## 3. Protección de versiones

```bash
./mvnw test -Dtest='PublishedStrategyVersionsTest'
```

Resultado esperado: pasa.

Prueba manual de US5:

1. Cambiar un peso de `OfficialStrategyV1` (por ejemplo, `GOALS` de MIDFIELDER de 0.25 a 0.26) y volver a correr el test. Tiene que fallar con el mensaje *"OFFICIAL v1 ya está publicada y su contenido cambió…"*.
2. Revertir el cambio.

## 4. Registro y consultas (H2)

```bash
./mvnw test -Dtest='PlayerQuoteRepositoryTest'
```

Resultado esperado:

- Las 4 consultas cumplen los escenarios US2-1 a US2-6, incluidos los bordes del rango, el empate por instante y la fecha anterior a la primera cotización.
- Modificar una cotización cargada y hacer `flush` no cambia la fila.
- El repositorio no expone ningún método `delete*`.
- Una cotización `CALCULATED` sin score se rechaza.

## 5. Consultas contra PostgreSQL real (e2e)

```bash
./mvnw test -Dgroups=e2e -Dtest='PlayerQuoteQueriesE2ETest'
```

En Windows: `.\mvnw.cmd test '-Dgroups=e2e' '-Dtest=PlayerQuoteQueriesE2ETest'`. Es la misma convención que en `specs/010-perfil-e2e/quickstart.md`.

Resultado esperado: los mismos resultados que en el paso 4 sobre los 5 jugadores de `E2EFixtures`. Esto incluye la cotización vigente al fin del día cuando hay una cotización a las `00:00:00` UTC del día siguiente: esa cotización no tiene que aparecer.

## 6. Gambetas (bloque B)

```bash
./mvnw test -Dtest='WhoScoredStatsMapperTest,WhoScoredAdapterTest,PlayerStatsServiceTest'
```

Resultado esperado:

- Con `league-dribbles.json`, cada jugador guarda sus gambetas.
- Si la consulta de gambetas falla, la liga se sincroniza igual, las gambetas quedan en `null` y el resto de las métricas no cambia.

## 7. Suite completa

```bash
./mvnw test -DexcludedGroups=e2e
./mvnw test -Dgroups=e2e
```

Los dos comandos tienen que terminar en `BUILD SUCCESS`, sin tests nuevos ignorados.