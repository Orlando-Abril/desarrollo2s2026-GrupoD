# Contrato: estrategias de valuación

**Feature**: `specs/013-cotizacion-modelo` | **Fecha**: 2026-10-14

Este contrato fija qué significa cada versión publicada. Lo leen los algoritmos de las features 015 (OFFICIAL) y 019 (MOMENTUM), y lo protege `PublishedStrategyVersionsTest`. Esta feature declara los valores pero no los interpreta: cómo se pasa del score al precio es responsabilidad de 015 y 019.

## 1. API del catálogo (`ValuationStrategyCatalog`)

| Operación | Resultado | Error |
|-----------|-----------|-------|
| `all()` | `List<ValuationStrategyDefinition>` con OFFICIAL v1 y MOMENTUM v1, en ese orden. Siempre devuelve ambas, sin importar cuál esté activa. | — |
| `find(String code)` | La definición con ese código. | `UnknownValuationStrategyException` si no existe: *"Estrategia de valuación desconocida: 'XYZ'. Códigos válidos: OFFICIAL, MOMENTUM"* |
| `active()` | La definición configurada en `valuation.active-strategy` (por defecto `OFFICIAL`). | Si el código no existe, el contexto de Spring no arranca y muestra el mismo mensaje. |

Reglas:

- Los códigos son únicos en el catálogo. Si hubiera un duplicado, el constructor lanza `IllegalStateException`.
- El catálogo expone una sola versión vigente por código.
- La propiedad `valuation.active-strategy` no se declara en ningún `.properties`. Se puede sobrescribir con `VALUATION_ACTIVE_STRATEGY=MOMENTUM` o con `-Dvaluation.active-strategy=MOMENTUM`.

## 2. Parámetros

| Estrategia | Versión | Tipo | `baseValue` | `scaleFactor` | `maxVariation` | `momentum` |
|------------|---------|------|-------------|---------------|----------------|------------|
| `OFFICIAL` | 1 | `OFFICIAL` | 1.00 | 0.10 | 0.20 (±20% por corrida) | — |
| `MOMENTUM` | 1 | `MOMENTUM` | 1.00 | 0.15 | 0.30 (±30% por corrida) | `window = 4`, `minHistory = 2` |

## 3. OFFICIAL v1: pesos por posición

El perfil de referencia es **MIDFIELDER**, que coincide exactamente con la fórmula de la consigna:
`score = 0.25·goals + 0.15·assists + 0.10·shots + 0.10·keyPasses + 0.10·dribbles + 0.10·tackles + 0.20·rating`.

| Métrica | GOALKEEPER | DEFENDER | MIDFIELDER (referencia) | FORWARD |
|---------|-----------:|---------:|------------------------:|--------:|
| `GOALS` | 0.00 | 0.10 | 0.25 | 0.30 |
| `ASSISTS` | 0.05 | 0.10 | 0.15 | 0.15 |
| `SHOTS` | 0.00 | 0.05 | 0.10 | 0.15 |
| `KEY_PASSES` | 0.10 | 0.10 | 0.10 | 0.05 |
| `DRIBBLES` | 0.00 | 0.05 | 0.10 | 0.10 |
| `TACKLES` | 0.15 | 0.30 | 0.10 | 0.05 |
| `RATING` | 0.70 | 0.30 | 0.20 | 0.20 |
| **Suma** | **1.00** | **1.00** | **1.00** | **1.00** |

Invariantes que verifican los tests:

- Todos los pesos son mayores o iguales a 0.
- En cada posición la suma es exactamente 1.00.
- MIDFIELDER es igual a la fórmula de la consigna.
- FORWARD tiene más peso en goles y tiros que MIDFIELDER, y DEFENDER tiene más peso en tackles.

## 4. MOMENTUM v1: pesos por posición

Pondera la forma reciente: el algoritmo 019 mira las últimas `window` cotizaciones y, si hay menos de `minHistory`, registra `INSUFFICIENT_HISTORY`. Además premia la disponibilidad (minutos jugados) y penaliza las tarjetas con peso negativo.

| Métrica | GOALKEEPER | DEFENDER | MIDFIELDER | FORWARD |
|---------|-----------:|---------:|-----------:|--------:|
| `GOALS` | 0.00 | 0.05 | 0.20 | 0.30 |
| `ASSISTS` | 0.00 | 0.05 | 0.15 | 0.15 |
| `SHOTS` | 0.00 | 0.00 | 0.05 | 0.10 |
| `KEY_PASSES` | 0.05 | 0.10 | 0.15 | 0.05 |
| `DRIBBLES` | 0.00 | 0.05 | 0.10 | 0.10 |
| `TACKLES` | 0.05 | 0.30 | 0.10 | 0.00 |
| `RATING` | 0.60 | 0.25 | 0.15 | 0.20 |
| `MINUTES` | 0.30 | 0.20 | 0.10 | 0.10 |
| **Suma de positivos** | **1.00** | **1.00** | **1.00** | **1.00** |
| `YELLOW_CARDS` | −0.05 | −0.10 | −0.05 | −0.05 |
| `RED_CARDS` | −0.20 | −0.20 | −0.15 | −0.15 |

Invariantes que verifican los tests:

- En cada posición, los pesos positivos suman 1.00.
- `YELLOW_CARDS` y `RED_CARDS` son negativos y, en cada posición, el de `RED_CARDS` es menor (más negativo) que el de `YELLOW_CARDS`.
- `MINUTES` es positivo en todas las posiciones.

## 5. Regla de versionado

1. Una versión publicada (las que tienen huella en `PublishedStrategyVersionsTest.PUBLISHED`) **no se modifica nunca**: ni pesos, ni `baseValue`, ni `scaleFactor`, ni `maxVariation`, ni `type`, ni `momentum`.
2. Para cambiar algo, se crea una clase nueva (por ejemplo `OfficialStrategyV2`) con `version = 2` y el catálogo pasa a exponerla en lugar de la v1. Después se agrega la entrada `"OFFICIAL:2" -> "<huella>"` a `PUBLISHED`, y la entrada de v1 se deja como registro histórico.
3. Las cotizaciones guardadas mantienen su `strategyVersion`, así que la historia sigue explicando con qué versión se calculó cada precio. Las consultas por estrategia incluyen todas las versiones.
4. Hay cambios que no alteran la huella y se pueden hacer sin subir la versión: el formato numérico (`0.10` → `0.1`), reordenar los enums y los comentarios.
5. Si se cambia un parámetro y se mantiene la versión, la suite falla con un mensaje que explica cómo crear la versión nueva.