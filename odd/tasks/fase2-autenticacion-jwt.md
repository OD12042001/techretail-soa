# Fase 2.1 — Autenticacion real: registro, login y JWT firmado

Branch: `feat/fase2-autenticacion-jwt` (desde `feat/fase1-desbloqueo-modulos` @ `0fe216b`) | Delivery: work-unit commits on the feature branch | Runner: `.\mvnw.cmd test` dentro de cada modulo (no hay POM agregador)

## Specs

- **S1** — "Implementa `POST /api/auth/registro` y `POST /api/auth/login` emitiendo el JWT real firmado, con expiración y roles (ADMIN, EMPLEADO, CLIENTE)."
  - Acceptance: `POST /api/auth/registro` crea una fila en `db_autenticacion.usuarios` con password BCrypt (cost 12) y rol `CLIENTE`, y responde `201` con `{idUsuario, usuario, nombre, rol, estado}`.
  - Acceptance: el rol **nunca** se toma del cuerpo publico; un `"rol":"ADMIN"` en la peticion se ignora (evita escalada de privilegios).
  - Acceptance: `POST /api/auth/login` valida credenciales y responde `200` con el contrato del Avance2 4.1.8: `{"token": "...", "tipo": "Bearer", "expiraEn": 3600, "rol": "CLIENTE", "idUsuario": 8, "nombre": "Maria Fernandez"}`.
  - Acceptance: el token es HS256 firmado con `techretail.security.jwt.secret`, `iss=techretail-auth`, con `exp` presente y claims `sub`, `rol`, `idUsuario`, `iat`, `exp` (Avance2 8.2).
  - Acceptance: los tres roles del catalogo (`ADMIN`, `EMPLEADO`, `CLIENTE`) viajan en el claim `rol` del token emitido.
- **S2** — "Asegúrate de tomar en cuenta la advertencia no bloqueante sobre restringir `/ws/facturacion/**` para que usuarios con rol CLIENTE no puedan invocar facturación."
  - Acceptance: un token `CLIENTE` sobre `/ws/facturacion/**` recibe `403` JSON **antes** del enrutamiento; `EMPLEADO`/`ADMIN` siguen alcanzando la ruta; sin token sigue siendo `401`.
- **S3** — "Mantén la estructura de respuestas en JSON estandarizado para los errores 401/403."
  - Acceptance: todo `401`/`403` del par Gateway+Autenticacion conserva `{"status", "error", "message"}` con `Cache-Control: no-store`, `X-Content-Type-Options: nosniff` y `X-Frame-Options: DENY`.
  - Acceptance: el `401` de login es **identico** para "usuario inexistente", "password incorrecta" y "usuario inactivo" (no hay enumeracion de usuarios).
- **S4** — "Al terminar la implementación de este módulo, ejecuta las pruebas/tests correspondientes para validar la emisión y parsing del token."
  - Acceptance: un test decodifica con el parser real el token devuelto por `/api/auth/login` y asserta `sub`, `rol`, `idUsuario`, `iss` y `exp`; la suite completa de `techretail-autenticacion` y `techretail-gateway` queda en verde.
- **S5** — (contrato documentado) Avance2 3.1: entrada `{usuario, password}`; Avance2 8.2: el BUS verifica firma, expiracion y rol y responde `401` sin llegar al servicio.
  - Acceptance: el HS256 emitido por `techretail-autenticacion` se valida con el `JwtService` del gateway usando el mismo secreto (contrato compartido, ya cubierto por los tests de Fase 1).
- **S6** — (criterio del profesor, ya vigente en Fase 1) "verificar que este protegido contra bypass de tokens, peticiones sin rol, inyecciones o fallos de validacion."
  - Acceptance: password BCrypt cost 12; longitud de password acotada a 8..72 bytes (limite real de BCrypt, un password mas largo da `400` y no `500`); `usuario` normalizado a minusculas para evitar cuentas duplicadas por mayusculas; duplicado `409`; ningun cuerpo de error expone stack trace, password ni token.

**Assumptions**

- **A1**: el registro publico solo puede crear `CLIENTE`. `ADMIN`/`EMPLEADO` se aprovisionan por un seeder de demo idempotente (`techretail.auth.demo-users.enabled`, `true` por defecto, `false` en el perfil `test`) porque el registro publico no puede elegir rol sin abrir una escalada de privilegios.
- **A2**: `expiraEn` se expresa en segundos, tal como muestra el ejemplo del Avance2 4.1.8 (`3600`).
- **A3**: `POST /api/auth/validar` (mencionado en Avance2 3.1) no forma parte de F2.1: la peticion del usuario nombra solo `registro` y `login`.

## Tasks

| ID | Specs | Route | Status | Evidence |
|----|-------|-------|--------|----------|
| T1 | S1, S6 | inline | done | `usuario/Usuario` + `usuario/RolEntidad` mapeadas contra `V1__create_tables.sql`; `ddl-auto=validate` arranca en los dos contextos de test (si el mapeo no coincidiera, el contexto no carga); `UsuarioRepository`/`RolRepository`; commit `d01d198` |
| T2 | S1, S3, S6 | inline | done | `AuthService` (BCrypt 12, hash senuelo, normalizacion a minusculas, limite de 72 bytes), `AuthController` (`201`/`200`), `ApiExceptionHandler` (400/401/409 con el sobre `{status,error,message}`); commit `d01d198` |
| T3 | S1, S6 | inline | done | `DemoUsuarioSeeder` idempotente para ADMIN/EMPLEADO/CLIENTE, apagable por `techretail.auth.demo-users.enabled` y apagado en el perfil `test`; commit `d01d198` |
| T4 | S2, S3 | inline | done | `SecurityConfig` del BUS: `"/ws/facturacion", "/ws/facturacion/**"` con `hasAnyRole(admin, empleado)`; commit `b31cda3` |
| T5 | S1, S4 | inline | done | `AuthControllerTests` (13) + `DemoUsuarioSeederTests` (3) + los 10 tests previos de contrato JWT; commit `d01d198` |
| T6 | S2, S4 | inline | done | `SeguridadYAuditoriaTests` 8/8 con los dos casos nuevos de facturacion; commit `b31cda3` |

Route declaration: all tasks inline. No writer delegation: the two units that could look parallel (autenticacion module vs gateway rule) share one Maven local repository and one H2 test database per module, and the gateway unit is a two-line rule plus one test — well under the "several minutes of work" threshold. The parent owns the whole diff.

## Log

- **L1 (user request, verbatim):**
  > ¡Excelente trabajo con la Fase 1! Procedamos de inmediato con la Fase 2.
  >
  > 1. OBJETIVO CLARO
  > Implementar la funcionalidad real de Negocio comenzando por el Módulo de Autenticación (F2.1) y la estructura base de auditoría/seguridad que requiere el profesor.
  >
  > 2. CONTEXTO Y AVANCES PREVIOS
  > - Fase 1 completada al 100%: 10 módulos configurados, Gateway WebMVC con JdbcTemplate grabando en `auditoria_logs`, y 40/40 tests en VERDE.
  > - Repositorio Git en la rama `feat/fase1-desbloqueo-modulos`.
  > - Requerimiento del profesor: Módulo de Auditoría en tiempo real, Seguridad sólida y resistencia a vulnerabilidades (evitar bypass de tokens, peticiones sin rol, inyecciones, etc.).
  >
  > 3. INSTRUCCIONES PARA LA FASE 2 (PASO A PASO)
  > - **Paso 1 (F2.1 - Autenticación):** Implementa `POST /api/auth/registro` y `POST /api/auth/login` emitiendo el JWT real firmado, con expiración y roles (ADMIN, EMPLEADO, CLIENTE).
  > - Asegúrate de tomar en cuenta la advertencia no bloqueante sobre restringir `/ws/facturacion/**` para que usuarios con rol CLIENTE no puedan invocar facturación.
  > - Mantén la estructura de respuestas en JSON estandarizado para los errores 401/403.
  > - Al terminar la implementación de este módulo, ejecuta las pruebas/tests correspondientes para validar la emisión y parsing del token.
  >
  > Por favor, confirma recepción y procede con el código de F2.1.

- **L2 (RED observado):** dos RED reales antes de implementar. Gateway: un token `CLIENTE` sobre `/ws/facturacion/servicio` **llego al servicio destino** (`jakarta.servlet.ServletException: ... I/O error on POST request for "http://localhost:8087/ws/facturacion/servicio"`), que es exactamente el bypass del aviso de Fase 1. Autenticacion: los 11 tests del contrato fallaron (`expected 201 but was 404`) porque los endpoints no existian.
- **L3 (hallazgo de plataforma):** Spring Boot 4.1.1 trae **Jackson 3** (`tools.jackson.databind`), no Jackson 2. El bean `ObjectMapper` del contexto es `tools.jackson`, mientras que `jjwt-jackson` 0.11.5 aporta Jackson 2 solo en runtime para el JWT. El test se escribio contra `tools.jackson`; no hizo falta tocar dependencias de produccion.
- **L4 (verificacion funcional):** `techretail-autenticacion` `Tests run: 26, Failures: 0, Errors: 0`; `techretail-gateway` `Tests run: 16, Failures: 0, Errors: 0`. Los 8 modulos de dominio no se re-ejecutaron porque `git diff 0fe216b..HEAD` no toca ningun archivo suyo (siguen con sus 2 tests de Fase 1).
- **L5 (contrato compartido, sin ejecucion conjunta):** la alineacion gateway/auth se sostiene en configuracion, no en un test cruzado (son proyectos Maven independientes): ambos `application.properties` declaran `techretail.security.jwt.secret=${JWT_SECRET:techretail-soa-dev-secret-key-2026-cambiar-en-produccion}` y `...issuer=${JWT_ISSUER:techretail-auth}`. Un test de `AuthController` valida el token con el `JwtService` real y asserta `iss=techretail-auth`.
- **L6 (verificador independiente: bloqueado por el runtime, degradacion honesta):** el agente `gentle-ai-verify` no pudo ejecutarse: 3 intentos terminaron en limite de tasa, reinicio del servidor y `Free usage limit reached` de OpenCode. Degradacion aplicada: el padre ejecuto 7 probes **ejecutables** (no leidos) sobre un clon limpio del candidato en `C:\Users\OD\AppData\Local\Temp\opencode\verify-f21-*`, fuera del workspace y borrado despues. Resultado `7/7` en verde: (P1) `rol`/`roles`/`authorities`/`idRol` en el registro siempre producen `CLIENTE`; (P2) passwords de 10 KB, 200 bytes, unicode, NUL, vacio, JSON truncado y `[]` responden 4xx, nunca 5xx; (P3) una firma alterada no valida y `exp - iat == expiraEn == 3600`, con `iss=techretail-auth`; (P4) el 401 conserva `no-store`/`nosniff`/`DENY` y no filtra hash, password ni stack trace; (P5) `CLIENTE` recibe `403` en las cinco variantes de `/ws/facturacion` (exacta, con barra final, subruta POST y GET); (P6) `EMPLEADO`/`ADMIN` no reciben 401/403 y el anonimo recibe `401`; (P7) `/api/monitoreo` e `/api/inventario` siguen en 403 para `CLIENTE` y `/actuator/health` sigue publico. **Ningun resultado es un PASS del verificador independiente**: es una verificacion del padre, declarada como tal.
- **L7 (RDD):** `gentle-ai review mode status` = on (global). `gentle-ai review assess --base-ref 0fe216b --committed-only` devolvio `risk: high` (`hot_path: .../auth/ApiError.java`, signal `auth`), 22 paths y 1052 lineas, `review_due: true`, `review_due_reason: high_risk`. El STATUS selectorless devolvio `action: start` con una transicion `collect / intended_untracked_selection_required` (archivos sin seguimiento: `.atl/` y este documento). La captura del revisor exige el mismo mecanismo de Task que el runtime tiene bloqueado por el limite de uso gratuito, asi que **no se inicio la revision y no existe autoridad ni aprobacion**: nada quedo consumido ni quemado. Para retomarla: commitear este documento, correr el STATUS con `--base-ref 0fe216b --committed-only=true` y resolver el `collect` cuando haya delegacion disponible.
- **L8 (entrega):** dos commits de unidad de trabajo en `feat/fase2-autenticacion-jwt` (`d01d198` autenticacion, `b31cda3` gateway). Sin push, sin PR y sin merge: siguen siendo decision del usuario.

