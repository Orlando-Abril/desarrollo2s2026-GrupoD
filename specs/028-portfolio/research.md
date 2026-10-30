# Research: Portfolio e historial de transacciones

Decisiones técnicas para `specs/028-portfolio`. Todas las incógnitas del contexto técnico quedan resueltas.

## D1 — Cálculo del precio promedio

- **Decision**: reconstruir por jugador el costo cronológicamente en `PortfolioService`, leyendo los trades del usuario por `executedAt ASC, id ASC`. Una compra suma cantidad y costo (`quantity × price`); una venta descuenta el costo al promedio exacto vigente; si la posición queda en cero, cantidad y costo se reinician.
- **Rationale**: satisface “una venta no cambia el promedio de lo que queda” y permite que una recompra después de cerrar la posición empiece un promedio nuevo. El desempate por id hace determinístico el resultado.
- **Alternatives considered**: `SUM(compras.total) / SUM(compras.quantity)` sobre toda la historia, descartado porque arrastra compras de posiciones ya cerradas; promedio sobre las compras aún “identificables”, descartado porque implicaría FIFO/LIFO, regla no solicitada.

## D2 — Precisión monetaria

- **Decision**: conservar costo con `BigDecimal` sin redondeo en cada evento; dividir con una precisión de cálculo suficiente y `HALF_EVEN`; normalizar sólo los campos de salida a escala 4. El costo de una venta total se fuerza a cero.
- **Rationale**: evita que el promedio mostrado, por ejemplo 1.0667, introduzca deriva al multiplicarse repetidamente. Coincide con la escala 4 de `Trade.price`, `Trade.total` y `Player.marketValue`.
- **Alternatives considered**: redondear el promedio después de cada compra o venta, descartado por acumulación de errores; `double`, descartado por no ser exacto para dinero.

## D3 — Costo inicial del superusuario

- **Decision**: identificar al superusuario mediante `SuperuserAccount`; por cada jugador emitido, su reconstrucción parte de 100 tokens a 1.0000 antes del primer trade. Como el superusuario no compra, una posición vendida por completo permanece cerrada.
- **Rationale**: la emisión no genera un trade, pero el enunciado fija el valor inicial de cada token en 1 crédito. Sin esta semilla el costo del portfolio del superusuario sería indefinido.
- **Alternatives considered**: crear trades ficticios de emisión, descartado porque altera el historial inmutable; promedio cero, descartado porque convierte todo el valor en ganancia artificial.

## D4 — Consultas sin N+1

- **Decision**: agregar proyecciones escalares:
  - `PortfolioHoldingRow`: holding + id/nombre/`marketValue` del jugador para todas las posiciones en una consulta.
  - `UserTradeRow`: trade + ids de comprador/vendedor + datos del jugador para costo e historial. El costo usa una lista masiva ascendente; el historial usa `Page<UserTradeRow>` descendente con `countQuery`.
- **Rationale**: la implementación nunca accede a `holding.player` o `trade.player` lazy después de cargar una lista. El número de consultas no crece con posiciones ni elementos de página.
- **Alternatives considered**: devolver entidades y usar `@EntityGraph`, viable pero expone más estado y hace más fácil reintroducir lazy loads; una consulta SQL recursiva que calcule costo, descartada por complejidad, menor portabilidad H2/PostgreSQL y dificultad para representar cierres/recompras.

## D5 — Alcance de “consulta agregada”

- **Decision**: definir “agregada” como consultas consolidadas/proyectadas para todos los elementos del usuario, no como `GROUP BY` monetario ni como una única sentencia. El presupuesto es constante dentro de cada modo de autorización: dueño, ADMIN o API key de servicio. Puede variar entre modos porque actor y objetivo no siempre son la misma cuenta, pero nunca crece por holding, trade o elemento de página.
- **Rationale**: un agregado SQL simple no puede cumplir el reinicio del promedio al cerrar una posición. Las consultas consolidadas eliminan el N+1 conservando la regla exacta.
- **Alternatives considered**: una consulta por posición, descartada por N+1; una única consulta cartesiana holdings × trades, descartada porque multiplica filas y memoria.

## D6 — Fuente de cantidad y consistencia

- **Decision**: `Holding` es la fuente autoritativa de cantidad total/reservada/disponible; los trades son la fuente del costo. El servicio verifica que la cantidad reconstruida coincida con la tenencia y trata una discrepancia como error interno de datos, sin inventar un promedio.
- **Rationale**: las reservas no aparecen en trades y deben seguir valuándose; la verificación detecta historias incompletas o escrituras fuera del motor.
- **Alternatives considered**: derivar también cantidad sólo de trades, descartado por emisión inicial y porque holdings es el estado confirmado actual; ignorar discrepancias, descartado por riesgo financiero.

## D7 — Autorización y precedencia 403/404

- **Decision**: resolver el actor antes que el objetivo. Si el actor es USER y el id difiere, lanzar 403 sin buscar el id objetivo. ADMIN puede consultar cualquier id y obtiene 404 si no existe. Una API key de servicio sin dueño, que el filtro existente marca `ROLE_ADMIN`, conserva acceso administrativo a `/users/{id}/...`.
- **Rationale**: cumple privacidad y evita enumeración de cuentas. Mantiene la semántica actual de las API keys de servicio.
- **Alternatives considered**: buscar primero y luego autorizar, descartado porque revela existencia; restringir todos los endpoints a JWT, descartado porque la API soporta JWT y API key como esquemas equivalentes.

## D8 — Identidad actual y API keys sin dueño

- **Decision**: `GET /auth/me` acepta JWT o API key con dueño, porque ambos principales son el username. Una API key de servicio sin dueño recibe 401 `authenticated_user_not_found`: está autenticada para tareas administrativas, pero no representa una cuenta personal.
- **Rationale**: no hay id, email, saldo ni portfolio propios que devolver para una credencial sin dueño. El frontend usa JWT y queda cubierto.
- **Alternatives considered**: 404, descartado porque el recurso “yo” carece de identidad autenticada y no debe revelar búsquedas; fabricar un usuario de servicio, descartado porque cambia el modelo.

## D9 — Ubicación de reglas

- **Decision**: `UserMarketController` sólo valida parámetros y mapea HTTP. `PortfolioService` aplica autorización, existencia, cálculo y mapeo de mercado. `AuthService.me` resuelve la identidad para que `AuthController` no acceda a repositorios.
- **Rationale**: respeta Controllers–Services–Repositories de la constitución y mantiene `AuthController` limitado al método nuevo autorizado.
- **Alternatives considered**: autorización/cálculo en controller, descartado por mezclar capas; un segundo servicio sólo para transacciones, descartado por duplicar autorización y resolución de usuario en una feature pequeña.

## D10 — Transacciones y consistencia de lectura

- **Decision**: métodos del servicio con transacción read-only y aislamiento `REPEATABLE_READ`. Holdings, trades, saldo y reservas se leen desde la misma fotografía confirmada al comenzar; no se toman locks de escritura.
- **Rationale**: `READ_COMMITTED` permitiría que consultas sucesivas vieran momentos diferentes durante un calce. `REPEATABLE_READ` evita esa mezcla sin bloquear escrituras normales del motor.
- **Alternatives considered**: aislamiento predeterminado, descartado por snapshot inconsistente; locks pesimistas, descartados porque una consulta no debe frenar órdenes; una única consulta cartesiana, descartada por multiplicación de filas; caché, descartada por riesgo de datos obsoletos.

## D11 — Contrato paginado

- **Decision**: `page` base cero (default 0), `size` default 20 y rango 1–100. Respuesta con `items`, `page`, `size`, `numberOfElements`, `totalElements`, `totalPages`, `first`, `last`. Orden fijo `executedAt DESC, id DESC`; se ignora cualquier sort externo.
- **Rationale**: coincide con la spec, limita consumo y evita saltos/duplicados por empates de timestamp.
- **Alternatives considered**: exponer `Page` directamente, descartado por contrato inestable y campos internos; cursores, descartados porque el requisito pide paginación convencional.

## D12 — Errores

- **Decision**: reusar `GlobalExceptionHandler.ErrorResponse`. Agregar `UserNotFoundException` → 404 `user_not_found`, `AuthenticatedUserNotFoundException` → 401 `authenticated_user_not_found` y `PortfolioInconsistentException` → 500 `portfolio_inconsistent` con mensaje público genérico. Parámetros inválidos siguen el 400 `validation_error`; acceso ajeno usa el 403 existente.
- **Rationale**: respuestas uniformes y documentables.
- **Alternatives considered**: `ResponseStatusException` en cada método, descartado por duplicación y shape menos controlado.

## D13 — DTOs

- **Decision**: records inmutables en `dto/market` y `dto/auth`. Los valores financieros se entregan como números decimales a escala 4. El tipo del historial es un enum de contrato `COMPRA`/`VENTA` desde la perspectiva del usuario.
- **Rationale**: evita exponer entidades JPA y refleja exactamente el vocabulario funcional.
- **Alternatives considered**: serializar entidades, descartado por relaciones lazy y fuga del modelo persistente; reutilizar `OrderSide`, descartado porque el historial no devuelve órdenes y el tipo es relativo al usuario consultado.

## D14 — OpenAPI

- **Decision**: anotaciones en controllers/records y test de `/v3/api-docs`. `/users` documenta `bearerAuth` y `apiKeyAuth`; `/auth/me` también documenta ambos, aclarando que la API key debe tener dueño. Todas incluyen `X-Correlation-ID`.
- **Rationale**: cumple §6.1 y sigue el patrón existente de `QuoteOpenApiTest`.
- **Alternatives considered**: YAML como única fuente ejecutable, descartado porque el proyecto genera OpenAPI desde anotaciones; el YAML de esta feature queda como contrato de diseño.

## D15 — Estrategia de tests

- **Decision**:
  - `PortfolioServiceTest`: compras a precios distintos, venta parcial, ventas/compras intercaladas, cierre/recompra, pérdida, reservas, superusuario, `marketValue` ausente, discrepancia de datos y snapshot concurrente.
  - `PortfolioQueryTest`: proyecciones traen jugador/cotización y orden correcto, más conteo de statements constante o verificación de que sólo se consumen escalares.
  - `UserMarketControllerTest`: MockMvc 200, 400, 401, 403, 404, 500, cuerpos sin filtraciones, ADMIN y paginación estable de 1.000 trades.
  - `AuthControllerTest`: agregar `/auth/me` 200 y 401 sin alterar tests existentes.
  - `UserMarketOpenApiTest`: rutas, parámetros, esquemas, seguridad y respuestas.
  - `PortfolioScenarioE2ETest`: 4 usuarios operando sobre 5 jugadores, como exige la constitución.
  - `ExternalSourcesDownE2ETest`: los tres GET responden con fuentes externas caídas.
  - `PortfolioReadOnlyE2ETest`: los tres GET producen cero cambios persistentes.
  - `PortfolioConsistencyE2ETest`: sobre PostgreSQL real, coordina con barreras una lectura y un calce concurrente que confirma entre las consultas del portfolio; la respuesta debe coincidir íntegramente con el estado anterior o posterior, nunca combinar saldo, holdings, reservas y trades de ambos estados.
  - `UserMarketPerformanceTest`: calentamiento y 5 mediciones sobre PostgreSQL/CI con 10.000 trades.
- **Rationale**: separa matemática, persistencia, seguridad/HTTP y documentación, y cubre explícitamente lo pedido por el equipo.
- **Alternatives considered**: sólo tests end-to-end, descartado porque dificultan aislar errores del promedio; sólo mocks, descartado porque no validan JPQL ni OpenAPI generado.
