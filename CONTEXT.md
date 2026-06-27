# InfyDamage — Documento de contexto y diseño

> Documento de referencia para el desarrollo del plugin. Describe el problema de combate en **Infinity Network**, la solución propuesta, la fórmula acordada y las integraciones del ecosistema. La documentación técnica operativa (comandos, permisos, API pública) se añadirá en `docs/` cuando exista implementación.

---

## 1. Qué es InfyDamage

**InfyDamage** (nombre provisional: *Infinity Combat Engine*) es un plugin para **Paper 1.21.11** en **Java 21** que reemplaza la mitigación de daño vanilla en combate PvP/PvE por una **fórmula configurable**, permitiendo que la progresión de equipo de **Nexo** y los encantamientos elevados (**THEnchantments**) tengan impacto real en combate.

No modifica ítems de Nexo ni reescribe configuraciones externas. Intercepta el daño en runtime, lee los valores reales del equipamiento y aplica la matemática custom.

### Integraciones previstas (soft dependencies)

| Plugin | Rol |
|--------|-----|
| **Nexo** | Armaduras y armas con atributos custom (+armadura, +daño) |
| **THEnchantments** | Encantamientos vanilla extendidos (Filo VII–X, Protección VII–X, etc.) |
| **MythicMobs** | Skills y daño de mobs/jugadores custom |
| **MythicCrucible** | Armas y habilidades con picos de daño intencionales |

---

## 2. El problema: caps vanilla de Minecraft

Durante pruebas en Infinity Network se detectó que equipar armaduras superiores de Nexo **no reducía el daño recibido** de forma proporcional al tier. El motor vanilla aplica dos techos duros:

### 2.1 Límite de armadura (20 puntos)

La mitigación por puntos de armadura se congela al llegar a **20 puntos** (~80 % de reducción). Cualquier punto adicional otorgado por armaduras de dimensiones superiores es **ignorado** por el juego.

### 2.2 Límite de encantamientos de protección (EPF 20)

El *Enchantment Protection Factor* se recorta automáticamente a un máximo de **20 puntos**. Un set completo de Protección V ya alcanza ese límite, por lo que Protección VII, VIII, IX o X mitigan **exactamente lo mismo** que Protección V en combate vanilla.

### 2.3 Consecuencia

La progresión de equipo del servidor existe en los ítems pero **no se refleja en el combate**. Jugadores con gear endgame reciben prácticamente el mismo daño que con netherite estándar.

---

## 3. Progresión de equipo en Infinity Network

El servidor estructura la economía y el equipo en cuatro etapas:

| Fase / Dimensión | Armadura (puntos de set) | Encantamiento máximo | Daño de espada (modificador) |
|------------------|--------------------------|----------------------|------------------------------|
| **Fase Base** (Mundo Vanilla) | Netherite estándar (20 pts) | Protección VII / Filo VII | Espada de Netherite base |
| **Dimensión 1** (Warden) | +1 pt por pieza (+4 total vs Netherite) | Protección VII / Filo VII | +2 daño base vs Netherite |
| **Dimensión 2** (El End / Len) | +2 pts por pieza (+8 total vs Netherite) | Protección VIII o IX | +3 daño base vs Warden |
| **Casos especiales** (Híbridos) | Netherite estándar (20 pts) | Protección X (ítems especiales) | Varía |

Los jugadores pueden craftear y encantar armaduras con normalidad en cualquier fase; la tabla describe el **contenido diseñado**, no una restricción del plugin.

### 3.1 Riesgo de casos híbridos

Un jugador con armadura de Netherite normal pero **Protección X** en piezas especiales será procesada por vanilla igual que Protección V. Con una fórmula mal calibrada, ese ítem podría mitigar **más** que un set del Warden con Protección VII.

El sistema debe ser lo suficientemente flexible para equilibrar artefactos especiales (overrides por ítem, caps de protección efectiva, etc.).

### 3.2 Sets del mismo tier sin encantar (diseño intencional)

Es válido que existan variantes del mismo tier de armadura con la misma Armadura/Toughness pero sin ningún encantamiento (ej.: set *Dragón* vs set *Vacío*, ambos con Armadura 32 y Toughness 20, pero el primero sin Protección y el segundo con Protección VII). Con la fórmula de InfyDamage activa, esa diferencia **sí** se va a notar en combate: el set sin encantar mitigará bastante menos, aunque comparta la misma Armadura. Esto es el comportamiento esperado, no un bug — confirmado como decisión de diseño.

---

## 4. Objetivo de pacing (ritmo de combate)

En simulaciones con fórmulas lineales estándar, un golpe endgame vs armadura endgame infligía ~**3.12 pts** (~1.5 corazones). Eso es demasiado alto para PvP competitivo con CPS elevado: los combates terminan en segundos y anulan estrategia, pociones de resistencia y mecánicas de curación custom.

### Objetivo acordado

| Contexto | Daño deseado por golpe base |
|----------|----------------------------|
| Endgame vs Endgame | **0.5 – 1 corazón** (1 – 2 pts de daño) |
| Picos de daño | Críticos y habilidades **MythicCrucible** (fuera de la fórmula principal) |

El combate debe sentirse como un MMORPG clásico: desgaste constante y controlado, con ventana para curación y táctica.

---

## 5. Solución: plugin interceptor de daño

### 5.1 Enfoque

1. **Interceptar** `EntityDamageByEntityEvent` (y variantes necesarias para proyectiles).
2. **Anular** la mitigación vanilla de armadura y protección (`DamageModifier.ARMOR` y `DamageModifier.MAGIC` → 0).
3. **Leer** atributos y encantamientos reales del equipamiento (sin caps vanilla).
4. **Aplicar** la fórmula exponencial configurable.
5. **Dejar aplicar después** los extras vanilla (pociones, críticos, resistencia) según §6.

No se modifican ítems de usuarios ni archivos de Nexo.

### 5.2 Flujo lógico

```
[Golpe entra al evento]
        ↓
[Anular mitigación vanilla ARMOR + MAGIC/EPF]
        ↓
[Leer DañoEquipo = DañoNexo + BonusFilo]     ← atacante
[Leer Defensa = Armadura + Protección]       ← defensor
        ↓
[DañoMitigado = DañoEquipo × (attenuation ^ PuntosDefensa) × global-modifier]
        ↓
[Aplicar mínimo: minimum-damage-per-hit]
        ↓
[Dejar que vanilla aplique extras: críticos, pociones, resistencia]
        ↓
[Daño final al jugador]
```

### 5.3 Nota técnica: orden correcto al tocar `EntityDamageEvent`

Bukkit ofrece dos formas de setear el daño del evento:

- `setDamage(DamageModifier, double)` → fija únicamente ese modifier.
- `setDamage(double)` (sin modifier) → fija la `BASE` y **recalcula los demás modifiers** (incluido `ARMOR`) en función del nuevo valor.

InfyDamage debe usar **siempre** la primera forma (`BASE`, `ARMOR`, `MAGIC` explícitos). Si en algún punto del código se llama a la segunda forma después de haber anulado `ARMOR`/`MAGIC`, Bukkit puede volver a aplicarles la mitigación vanilla por detrás, sin lanzar ningún error. Este es el bug más probable detrás de síntomas tipo "subí Filo/Protección y no cambió nada en combate".

Alternativa más segura: cancelar el evento (`setCancelled(true)`) y aplicar el resultado final directo con `entity.setHealth()`, evitando por completo el sistema de modifiers de Bukkit.

### 5.4 Configuración recargable en caliente (requisito de diseño)

Los valores de la fórmula (`attenuation-factor`, `protection-weight`, `toughness-weight`, `global-damage-modifier`, `power.bonus-per-level`, `minimum-damage-per-hit`, etc.) **no pueden vivir como constantes fijas en el código**. Deben guardarse en un objeto de configuración que se pueda reconstruir en caliente, para poder calibrar el balance sin reiniciar el servidor.

Implementación esperada: comando `/infydamage reload` que llama a `reloadConfig()` y reconstruye ese objeto desde el `config.yml` en disco. No requiere recompilar ni reiniciar el JVM.

---

## 6. Alcance de la fórmula (decisión confirmada)

### 6.1 Qué ENTRA en la fórmula

Solo lo que define el **equipo permanente** del jugador. **Importante (corregido en Fase 4 de `IMPLEMENTATION_PLAN.md`):** el lado del atacante NO se recalcula a mano. Se confía en `DamageModifier.BASE` del evento, que vanilla ya calcula bien — ahí adentro están el atributo de daño de Nexo, el bonus real de Filo (sin cap, vanilla nunca lo limita) y los extras de §6.2 que tienen que quedar fuera de la fórmula (Fuerza/Debilidad, críticos). Si se reemplazara `BASE` con un cálculo propio, esos extras se perderían en silencio — por eso la decisión original de "calcular Filo a mano sin depender del evento" quedó revertida. El bug real de §2 está únicamente en `ARMOR` y `MAGIC` (los dos modifiers que vanilla capa a 20 puntos), así que son los únicos dos que InfyDamage reemplaza.

**Solo melee, y solo si el atacante es un jugador (ver §6.1.3):** la fórmula nunca toca proyectiles (arco/ballesta quedan 100% vanilla, decisión explícita del dueño del server) ni golpes donde el atacante sea un mob vanilla o un mob de MythicMobs.

**Atacante — Daño de equipo**

| Componente | Fuente |
|------------|--------|
| Daño de equipo (melee, solo atacante jugador) | `event.getDamage(DamageModifier.BASE)` — vanilla ya suma ahí el atributo `Attribute.ATTACK_DAMAGE` (incluye el modificador de Nexo) + el bonus real de Filo, sin cap |

**Defensor — Puntos de defensa**

| Componente | Fuente |
|------------|--------|
| Puntos de armadura | Suma de `Attribute.ARMOR` de las piezas equipadas |
| Resistencia al impacto (Toughness) | Suma de `Attribute.ARMOR_TOUGHNESS` de las piezas equipadas, ponderada por `toughness-weight` |
| Protección | Suma de niveles de Protección en cada pieza de armadura |

```
PuntosDefensa = ArmaduraTotal + (ToughnessTotal × toughness-weight) + (SumaProtección × protection-weight)

DañoMitigado = DañoEquipo × (attenuation ^ PuntosDefensa) × global-damage-modifier

DañoMitigado = max(DañoMitigado, minimum-damage-per-hit)
```

> `DañoEquipo` = el valor de `DamageModifier.BASE` del evento en el momento del golpe melee. Los proyectiles (arco/ballesta) ya no pasan por la fórmula en absoluto — ver §6.1.3.

**Aplicación en el evento:** `ARMOR` se setea a `0`, y toda la reducción custom (`DañoMitigado - DañoEquipo`, un valor negativo) se mete en `MAGIC`. Así, `BASE + MAGIC = DañoMitigado`, y `RESISTANCE`/`ABSORPTION`/`BLOCKING`/`HARD_HAT` siguen sin tocarse — vanilla los sigue aplicando después, tal como pide §6.2.

**¿Y `sharpnessBonus`/`equipmentDamage` de `DamageFormula`?** Esas funciones puras siguen existiendo para el comando `/infydamage simulate` (Fase 6), que no tiene un evento real del cual leer `BASE` y necesita recalcular el daño a mano a partir de los atributos/encantamientos de un ítem. El listener de combate real (Fase 4) no las usa. `powerBonus` se eliminó junto con el soporte de proyectiles — ver §6.1.3.

**Por qué se agregó Toughness:** sets de Nexo como *Vacío* y *Dragón* invierten puntos de `ARMOR_TOUGHNESS` por pieza (5 en cada una, en los ejemplos vistos). Si la fórmula solo usara Armadura + Protección, esa inversión quedaría decorativa apenas InfyDamage entre en juego. Queda pendiente de calibración el valor de `toughness-weight` en pruebas de balance.

### 6.1.1 Umbral mínimo: la fórmula es para ARMAS, no para cualquier golpe

**Bug encontrado:** `minimum-damage-per-hit` se aplicaba a *cualquier* `ENTITY_ATTACK`/`PROJECTILE`, sin distinguir si el atacante pegó con una espada o con la mano vacía/una manzana/un snowball. Resultado: un puño (que en vanilla hace ~1 de daño) o un proyectil sin fuerza (que hace 0) quedaban **subidos artificialmente** al piso de seguridad, porque el listener reemplazaba `ARMOR`/`MAGIC` igual y aplicaba el `max(...)` sin filtrar por arma.

**Decisión:** la fórmula custom es para **armas que se calibran a propósito** (espadas/hachas vanilla, picos/palas de Nexo con `ATTACK_DAMAGE` custom como `dragon_pico_nolore`/`dragon_pala_nolore`). Todo lo demás tiene que comportarse 100% vanilla.

**Mecanismo elegido — umbral de daño, no lista de Materials/IDs:** en vez de inspeccionar el `ItemStack` en mano del atacante, se gatea directo sobre `event.getDamage(DamageModifier.BASE)` — el mismo valor que el listener ya lee para la fórmula:

```
si DañoEquipo (= BASE) ≤ scope.weapon-damage-threshold (default 1.0, la base vanilla de puño vacío)
   → no se toca el evento, vanilla aplica su propia mitigación completa
```

Ventaja sobre una lista de Materials/IDs: cubre **proyectiles automáticamente** (un snowball o una flecha sin carga con daño ≤ umbral también queda afuera) sin código adicional, y no hay que mantener una lista cada vez que Nexo agregue un arma nueva. Ventaja sobre revisar el ítem del atacante: no depende de si el arma es de Nexo o 100% vanilla craftada/encantada por un jugador — el camino es el mismo para los dos (ver §6.1.2).

### 6.1.2 Confirmación: la fórmula no depende de Nexo

Todo lo que lee el listener viene de la API estándar de Bukkit (`DamageModifier.BASE`, `Attribute.ARMOR`/`ARMOR_TOUGHNESS`, `Enchantment.PROTECTION`), sin ninguna rama que distinga si el ítem es de Nexo o craftado/encantado a mano por un jugador. `NexoHook` solo se usa para el override **opcional** de Fase 7 (`protection-cap-by-item-id`); si la pieza no resuelve a un ID de Nexo, no se aplica ningún cap y se usa el nivel real del encantamiento — el mismo resultado que un ítem 100% vanilla.

### 6.1.3 El atacante debe ser un jugador; proyectiles y MythicMobs `ignorearmor` quedan 100% fuera

**Pedido del dueño del server:** un Zombie o un Warden pegándole a un jugador deben hacer su daño vanilla normal, sin que InfyDamage lo toque. Lo mismo si quien pega es un mob de MythicMobs (vía skill o ataque cuerpo a cuerpo nativo). La fórmula custom es exclusivamente para cuando **un jugador** pega con un arma — no para calibrar el daño que reciben los jugadores de parte de mobs.

**Mecanismo:** `CombatDamageListener` chequea `event.getDamager() instanceof Player` justo después del filtro de `LivingEntity` defensor, antes de leer `BASE` o tocar cualquier modifier. Si el atacante no es un jugador, el evento queda intacto — vanilla (o MythicMobs, si el mob es Mythic) aplica su propia mitigación completa.

Cuando el atacante **sí** es un jugador pero el **defensor** es un mob (vanilla o Mythic), la fórmula sigue aplicando del lado de la defensa — un mob con armadura custom (vía atributos) se sigue beneficiando de §6.1 igual que un jugador. El whitelist/blacklist de `mythicmobs-scope` (§9.3) sigue funcionando para excluir tipos de mob puntuales como defensores.

**MythicMobs `ignorearmor` (true damage):** aunque el atacante sea un jugador, un golpe de MythicMobs marcado con `ignorearmor` (true damage) no debe pasar por la fórmula — es una mecánica explícita de "ignora toda mitigación", y la fórmula custom de InfyDamage es justamente mitigación. Como `MythicDamageEvent` (evento propio de MythicMobs, distinto de `EntityDamageByEntityEvent`) se dispara antes en el mismo tick, `MythicTrueDamageListener` lo escucha en prioridad `MONITOR`, lee `event.getDamageMetadata().getIgnoresArmor()` y, si es `true`, marca al defensor (`UUID`) en un `Set` compartido. `CombatDamageListener` consume esa marca (`consumeTrueDamage`) antes de aplicar la fórmula; si está marcada, se salta el golpe entero. Una tarea programada a un tick limpia la marca como red de seguridad, por si el `EntityDamageByEntityEvent` correlacionado nunca llega a dispararse (ej. la skill termina cancelando el daño por otro motivo).

**Proyectiles eliminados del alcance:** arcos/ballestas (y el feature de Power que los acompañaba) se removieron por completo del plugin — `scope.apply-to` ya no incluye `PROJECTILE`, y `DamageFormula.powerBonus`/`DamageFormulaConfig.powerUseVanillaFormula`/`powerBonusPerLevel` se borraron del código. Decisión explícita: "que se comporten como Minecraft lo hace, sin que nosotros tengamos algo que ver con eso" — Power vanilla ya es suficientemente fuerte sin necesitar una curva de balance propia.

### 6.2 Qué queda FUERA de la fórmula (extras)

Se aplican **después** de la mitigación custom, como en combate vanilla:

| Extra | Tratamiento |
|-------|-------------|
| Fuerza / Debilidad | Fuera de la fórmula; vanilla después |
| Resistencia (pociones) | Fuera de la fórmula; vanilla después |
| Otros efectos de pociones | Fuera de la fórmula |
| Críticos | Fuera de la fórmula; multiplicador vanilla después |
| Habilidades MythicCrucible | Capa aparte o bypass (picos intencionales) |

Las pociones y buffs temporales siguen importando en pelea, pero **no distorsionan** el balance entre tiers de equipo.

### 6.3 Glosario de términos

| Término | Significado |
|---------|-------------|
| **Fase Base** | Primera tier de progresión del servidor (netherite estándar). Concepto de diseño de contenido. |
| **Daño de equipo** | `DamageModifier.BASE` del evento real (ya incluye daño Nexo + Filo, sin recalcular nada) — input del atacante en la fórmula del plugin. **No** confundir con Fase Base. |
| **Puntos de defensa** | Armadura + Toughness ponderada + Protección ponderada. Input del defensor en la fórmula. |

---

## 7. Modelo matemático y config de referencia

### 7.1 Fórmula seleccionada: exponencial

Recomendada para evitar colapso con Protección X y escalar suavemente en endgame:

```
DañoFinal (dentro de la fórmula) = DañoEquipo × (attenuation ^ PuntosDefensa) × global-damage-modifier
```

### 7.2 `config.yml` de referencia

```yaml
# Infinity Combat Engine — InfyDamage
combat-pacing:
  # Multiplicador global: combates más largos con valores menores
  global-damage-modifier: 0.45

formulas:
  # DañoFinal = DañoEquipo × (attenuation ^ PuntosDefensa)
  # PuntosDefensa = ArmaduraTotal + (ToughnessTotal × toughness-weight) + (SumaProtección × protection-weight)
  attenuation-factor: 0.965
  protection-weight: 1.2
  toughness-weight: 1.0   # pendiente de calibrar con pruebas de balance

  # Límites de seguridad
  minimum-damage-per-hit: 0.5   # Ningún golpe normal < ¼ corazón

sharpness:
  # Filo usa la curva vanilla (0.5 × nivel + 0.5), calculada por InfyDamage
  # a partir del nivel real leído por API — sin tope, sin depender del evento.
  use-vanilla-formula: true

scope:
  # Tipos de daño que pasan por la fórmula custom. Solo ataque cuerpo a cuerpo.
  apply-to:
    - ENTITY_ATTACK
  # Daño ambiental, fuego, caída, proyectiles, etc. → vanilla sin interceptar
```

> **Nota (§6.1.3):** ya no existe sección `power:` — arcos/ballestas y el feature de Power se eliminaron del plugin por completo, decisión explícita del dueño del server. Los valores numéricos de este bloque (`global-damage-modifier`, `attenuation-factor`, `protection-weight`, `toughness-weight`) quedan como referencia histórica de cuando se escribió esta sección; el `config.yml` real en disco puede tener otros valores tras calibración (Fase 8) — no se sincronizan automáticamente con este documento.

### 7.3 Simulación endgame (referencia de balance)

**Ataque:** Espada del End (~14 pts Nexo + Filo VII ≈ 18 pts de daño de equipo)

**Defensa:** Set del End completo (28 armadura, 20 toughness — 5 por pieza × 4) + Protección IX × 4 piezas (36 niveles)

```
PuntosDefensa = 28 + (20 × 1.0) + (36 × 1.2) = 91.2
Reducción     = 0.965 ^ 91.2                  = 0.0388  (recibe ~3.9 % del daño)
Daño bruto    = 18 × 0.0388                    = 0.70 pts
Con global    = 0.70 × 0.45                    = 0.31 pts → pisa el suelo de minimum-damage-per-hit (0.5 pts)
```

> **Nota de calibración (actualizada tras sumar Toughness a la fórmula en §6.1):** con los pesos default, este combo de endgame ya pisa el `minimum-damage-per-hit` en vez de aterrizar naturalmente en el rango objetivo de §4 (0.5–1 corazón). No es un error de la fórmula — es la señal de que `attenuation-factor` y/o `toughness-weight` quedaron demasiado agresivos una vez que Toughness entró a `PuntosDefensa`, y hay que recalibrarlos en la Fase 8 de `IMPLEMENTATION_PLAN.md` con el comando `simulate`, no a ojo en este documento.

Un jugador con Netherite estándar frente a la misma espada recibiría castigo significativamente mayor, validando el farmeo en dimensiones superiores.

---

## 8. Lectura técnica de atributos (implementación)

> Nombres de atributo confirmados contra el JAR real de `paper-api 1.21.11-R0.1-SNAPSHOT` (cambiaron en 1.21+: sin el prefijo `GENERIC_` que tenían en versiones viejas de Bukkit).

| Dato | API / origen |
|------|----------------|
| Daño de equipo (melee, atacante jugador) | `event.getDamage(DamageModifier.BASE)` en el listener real (CONTEXT.md §6.1) — ya incluye `Attribute.ATTACK_DAMAGE` + Filo real |
| Daño Nexo (solo para `/infydamage simulate`, sin evento real) | Valor final de `Attribute.ATTACK_DAMAGE` del ítem en mano principal |
| Filo (solo para `simulate`) | `Enchantment.SHARPNESS` — nivel real almacenado (THEnchantments permite > V); bonus calculado con la curva vanilla |
| Armadura | Suma de `Attribute.ARMOR` de casco, pechera, pantalones, botas (+ offhand si aplica) |
| Toughness | Suma de `Attribute.ARMOR_TOUGHNESS` de las mismas piezas |
| Protección | Suma de niveles de `Enchantment.PROTECTION` por pieza |

Identificación opcional de ítems Nexo vía namespace `nexo:` en `item_model` o API Nexo para overrides de balance por ítem.

---

## 9. Integraciones y convivencia

### 9.1 Nexo

Solo lectura de atributos del ItemStack. No requiere hook activo salvo overrides por ID de ítem.

### 9.2 THEnchantments

Encantamientos con niveles > V ya existen en el ecosistema (hasta 50 en config). El plugin lee el nivel real del encantamiento; vanilla lo capa en combate, InfyDamage no.

### 9.3 MythicMobs

La mayoría de skills pasan por `EntityDamageByEntityEvent`. Atacantes Mythic (mobs) ya quedan fuera por el gate de §6.1.3 (atacante debe ser `Player`). Whitelist/blacklist por tipo de mob para **defensores** Mythic vía `mythicmobs-scope.mode`/`mob-types` (§12).

**True damage (`ignorearmor`):** cubierto por `MythicTrueDamageListener` (§6.1.3) — escucha el evento propio `MythicDamageEvent` (no `EntityDamageByEntityEvent`) en prioridad `MONITOR`, y si `DamageMetadata.getIgnoresArmor()` es `true`, marca al defensor para que `CombatDamageListener` se salte ese golpe puntual aunque el atacante sea un jugador. Caveat documentado: el mecanismo asume que `MythicDamageEvent` se dispara en el mismo tick, antes del `EntityDamageByEntityEvent` correlacionado — razonable para skills de un solo objetivo; para skills de área que procesen varios objetivos, el `Set<UUID>` (no un solo valor) cubre el caso de múltiples defensores simultáneos, y la limpieza programada a un tick evita que una marca quede pegada para siempre si el evento correlacionado nunca llega a dispararse.

### 9.4 MythicCrucible

Armas y habilidades con daño intencionalmente alto deben poder **saltarse la fórmula** o aplicarse como capa posterior para preservar picos de daño.

### 9.5 Orden de eventos

Usar prioridades claras en el listener (`HIGH` para anular vanilla, aplicar fórmula). Flag/metadata interno para evitar doble procesamiento con otros plugins de combate.

---

## 10. Alcance del interceptor

| Tipo de daño | ¿Pasa por InfyDamage? |
|--------------|----------------------|
| Golpe cuerpo a cuerpo, atacante jugador (espada, hacha, etc.) | Sí |
| Golpe de mob vanilla o MythicMobs contra un jugador | No — 100% vanilla/Mythic (§6.1.3) |
| Proyectiles (arco, ballesta, etc.) | No — 100% vanilla, decisión explícita (§6.1.3) |
| Daño MythicMobs con `ignorearmor` (true damage) | No, aunque el atacante sea jugador (§6.1.3) |
| Caída, fuego, ahogamiento, veneno | No (vanilla) |
| `/kill`, daño de comando | No |
| Skills Mythic con bypass configurado | No / capa aparte |

Secciones futuras en config: multiplicadores separados `pvp` vs `pve` si el balance lo requiere.

---

## 11. Herramientas y estructura técnica

### 11.1 Convenciones del proyecto

| Aspecto | Valor |
|---------|-------|
| Runtime | Paper **1.21.11**, Java **21** |
| Build | **Maven** (`pom.xml`) — patrón InfyContratos / InfyCodex |
| Carpeta | `InfyDamage/` (greenfield) |
| Package | `me.gadisplay.infydamage` (mismo prefijo que `me.gadisplay.infycontratos`) |

### 11.2 Patrón de referencia: THCore

Se toma `THCore` (`C:\Users\PC\Documents\Descomprimir\THCore-main`) como **referencia de organización de código**, no como dependencia en runtime ni como referencia de build tool. InfyDamage no importa ni depende de THCore — solo se calca el patrón de estructura para las piezas que coinciden en necesidad. THCore usa Gradle, pero InfyDamage usa **Maven** (ver 11.1) para mantener consistencia con InfyContratos/InfyCodex, que conviven en el mismo workspace.

**Bootstrap:** `InfyDamage extends JavaPlugin`, con orden fijo en `onEnable()`:
1. `ConfigManager` (carga `config.yml`)
2. `HookManager` (detecta soft-dependencies disponibles)
3. Listener de `EntityDamageByEntityEvent`
4. Comando `/infydamage` con sus subcomandos

**Config recargable en caliente** (resuelve el requisito de §5.4): `ConfigManager.reload()` llama a `plugin.reloadConfig()` y reconstruye un objeto `DamageFormula` en memoria a partir de los valores frescos del YAML — sin recompilar ni reiniciar el JVM.

**Framework de comandos** (3 capas, evita un switch gigante):
- `SubCommand` (interfaz): `getName()`, `getPermission()`, `execute(sender, args)`, `tabComplete(sender, args)`.
- `BaseCommand` (abstracta): mapa de subcomandos registrados, valida permisos, delega ejecución.
- `InfyDamageCommand extends BaseCommand`: registra los subcomandos `reload` y `simulate <atacante> <defensor>` (pendientes de §12).

**Soft-dependencies (hooks):** `BaseHook` abstracta con `tryLoad()` — verifica si el plugin externo está presente y habilitado antes de cachear su API; si falla o no está instalado, el hook queda deshabilitado sin romper el arranque. `HookManager` registra y expone cada hook por clase (`getHook(NexoHook.class)`), y el listener de daño lo consulta con null-check antes de usarlo.

Hooks necesarios para InfyDamage: `NexoHook`, `THEnchantmentsHook`, `MythicMobsHook`, `MythicCrucibleHook` — alineados con la tabla de integraciones de §1. En `plugin.yml`, los cuatro van en `softdepend`, no en `depend`.

---

## 12. Pendientes de diseño / fase 2

- [x] Overrides por `nexo:item_id` para casos híbridos (Netherite + Prot X) — `overrides.protection-cap-by-item-id`
- [x] Cap de protección efectiva — fusionado con el override por ítem (un solo mecanismo, ver historial de decisiones)
- [x] Whitelist/blacklist de fuentes MythicMobs — `mythicmobs-scope.mode`/`mob-types`
- [ ] Whitelist/blacklist de fuentes **MythicCrucible** — sigue bloqueado, MythicCrucible no compila (§0/§9.4)
- [x] Comando `/infydamage simulate <atacante> <defensor>` para balance sin entrar al servidor
- [x] Comando `/infydamage reload` para recargar `config.yml` en caliente, sin reiniciar el servidor
- [ ] Secciones `pvp` / `pve` con multiplicadores distintos
- [ ] API pública para que otros plugins Infy consulten daño calculado
- [ ] Documentación operativa en `docs/`

---

## 12.1 Rendimiento y escalabilidad (auditoría)

Pregunta del dueño del server: ¿esto se nota en un profile de Spark con 100 jugadores? Auditoría del único código que corre en el hot path (`CombatDamageListener#onEntityDamageByEntity`, dispara en CADA `EntityDamageByEntityEvent` del server):

| Aspecto | Estado |
|---------|--------|
| Tareas repetitivas (scheduler) | **Ninguna.** No hay un solo `Bukkit.getScheduler()` en todo el plugin — costo cero cuando no hay combate, a diferencia de plugins que polean cada tick. |
| Estado por jugador (memoria) | **Ninguno.** No hay ningún `Map` indexado por `Player`/`UUID` en todo el código — cero riesgo de memory leak por jugadores que entran y salen durante semanas de uptime. |
| I/O en el hot path | **Ninguno.** El único acceso a disco de todo el plugin es `saveDefaultConfig()` al arrancar — el listener no lee ni escribe archivos. |
| Costo por golpe | Un par de lookups O(1) (`EnumSet.contains`, `HashMap.get` cacheado), 2-4 lecturas de atributo/encantamiento del defensor, un `Math.pow` — del orden de microsegundos. Para que esto importe en un profile haría falta un volumen de golpes por segundo que ningún server de 100 jugadores sostiene en la práctica. |
| Orden de los checks | Ya está de más barato a más caro: scope → tipo de entidad → umbral de arma (filtra la mayoría de los golpes triviales antes de tocar nada de Mythic/Nexo) → exclusión Mythic → lectura de atributos. |

**Optimizaciones aplicadas tras la auditoría** (sin cambiar ningún resultado, verificado con los 9 tests):
- `CombatDamageListener` cachea `NexoHook`/`MythicMobsHook` en el constructor en vez de hacer `HashMap.get()` dos veces por golpe — los hooks no cambian después del arranque, así que el lookup repetido era trabajo tirado.
- `EquipmentReader.sumProtectionLevels` ya no llama a `NexoItems.idFromItem` (API de un plugin externo) por cada pieza de armadura cuando `overrides.protection-cap-by-item-id` está vacío — que es el caso por default. Antes se pagaba esa llamada en cada golpe sin que pudiera cambiar nada el resultado.

**Veredicto:** la arquitectura escala bien para 100 jugadores tal como está. El cuello de botella de un server grande nunca va a ser este plugin — va a ser MythicMobs, Nexo (su propio render de ítems) o la cantidad de entidades cargadas, que están fuera del control de InfyDamage.

---

## 13. Historial de decisiones

| Fecha | Decisión |
|-------|----------|
| 2026-06-26 | Problema diagnosticado: caps vanilla 20 armadura / 20 EPF anulan progresión Nexo |
| 2026-06-26 | Fórmula exponencial elegida sobre lineal para pacing endgame y casos Prot X |
| 2026-06-26 | **Alcance de fórmula:** solo DañoNexo + Filo (atacante) y Armadura + Protección (defensor) |
| 2026-06-26 | Pociones, críticos y habilidades MythicCrucible **fuera** de la fórmula; se aplican después |
| 2026-06-26 | Caso real detectado en ítems Nexo (`tugkandeman_void_equipment.yml`): sets con misma Armadura pero distinta Protección (Vacío vs Dragón) mitigaban casi igual en vanilla — confirma el diagnóstico de §2 |
| 2026-06-26 | **Toughness entra a la fórmula:** `PuntosDefensa` suma `ArmaduraTotal + (ToughnessTotal × toughness-weight) + (SumaProtección × protection-weight)` — evita que `ARMOR_TOUGHNESS` quede decorativo |
| 2026-06-26 | **Power tiene curva propia**, independiente de Filo (`power.bonus-per-level`), no la curva vanilla |
| 2026-06-26 | **Filo usa la curva vanilla** (`0.5 × nivel + 0.5`) pero calculada por InfyDamage desde el nivel real leído por API, sin depender del modifier `BASE` del evento |
| 2026-06-26 | Sets del mismo tier sin encantar (ej. Dragón) son intencionalmente más débiles en defensa una vez activa la fórmula — confirmado, no es bug |
| 2026-06-26 | Config de fórmula debe ser recargable en caliente (`/infydamage reload`); prohibido hardcodear valores de balance como constantes en código |
| 2026-06-26 | **Build confirmado: Maven**, consistente con InfyContratos/InfyCodex. THCore usa Gradle, pero solo se toma como referencia de estructura de código, no de build tool |
| 2026-06-26 | Estructura técnica calcada de **THCore** (referencia de organización, no dependencia en runtime): bootstrap con `JavaPlugin`, `ConfigManager` recargable, framework de comandos en 3 capas (`SubCommand`/`BaseCommand`/comando concreto), y `BaseHook`/`HookManager` para las soft-dependencies (Nexo, THEnchantments, MythicMobs, MythicCrucible) |
| 2026-06-26 | Package confirmado: `me.gadisplay.infydamage`, igual prefijo que `me.gadisplay.infycontratos` |
| 2026-06-26 | **THEnchantments no necesita hook ni dependencia Maven** — confirmado leyendo su código fuente: aplica niveles extendidos con `meta.addEnchant(enchantment, level, true)`, dato vanilla estándar legible con la API normal de Bukkit |
| 2026-06-26 | **MythicCrucible bloqueado**: el repo Maven de Lumine tiene el POM roto en todas las versiones publicadas (`<parent>` con property sin resolver). Se avanza sin esa dependencia; el hook queda pendiente hasta conseguir el JAR real |
| 2026-06-26 | **Simulación de §7.3 corregida** para incluir Toughness (quedó desactualizada cuando se agregó a la fórmula en §6.1). Con los pesos default, el combo de endgame ahora pisa `minimum-damage-per-hit` — queda marcado como pendiente de recalibrar en Fase 8, no se ajustó `attenuation-factor` a ciegas |
| 2026-06-26 | **Corrección arquitectónica importante (Fase 4):** se revierte la decisión de calcular `BonusFilo`/`BonusPower` a mano e ignorar el evento. El listener real confía en `DamageModifier.BASE` de vanilla (ya incluye Nexo + Filo/Power reales) y reemplaza ÚNICAMENTE `ARMOR`+`MAGIC`. Motivo: recalcular `BASE` a mano borraba en silencio Fuerza/Debilidad y críticos, que §6.2 exige dejar fuera de la fórmula pero funcionando "vanilla después". Esto también resolvió sin ambigüedad el caso de proyectiles: mismo código sirve para melee y flechas, sin distinguir tipo de arma |
| 2026-06-26 | **Nombres de atributo corregidos:** en Paper 1.21+ son `Attribute.ARMOR`/`ARMOR_TOUGHNESS`/`ATTACK_DAMAGE`, sin el prefijo `GENERIC_` de versiones viejas de Bukkit — confirmado contra el JAR real de `paper-api 1.21.11-R0.1-SNAPSHOT`, no asumido |
| 2026-06-26 | **Fase 7 (overrides) implementada:** "cap por ítem" y "cap por tier" se fusionaron en un solo mecanismo (`overrides.protection-cap-by-item-id`) — un admin lista cada ítem híbrido por su ID Nexo, en vez de mantener dos sistemas redundantes |
| 2026-06-26 | **Whitelist/blacklist de MythicMobs implementado** (`mythicmobs-scope.mode`/`mob-types`); MythicCrucible queda explícitamente fuera de este mecanismo porque sigue bloqueado (repo Maven roto, §0) |
| 2026-06-26 | **Bug encontrado y corregido (dueño de server):** `minimum-damage-per-hit` subía artificialmente golpes de puño/manzana/proyectiles sin fuerza, porque la fórmula se aplicaba a cualquier `ENTITY_ATTACK`/`PROJECTILE` sin distinguir arma real. Fix: `scope.weapon-damage-threshold` (default 1.0) gatea sobre `DamageModifier.BASE` antes de tocar cualquier modifier — ver §6.1.1 |
| 2026-06-26 | **Auditoría de balance Warden+Prote7 vs Netherite+Prote7** (pedida como chequeo general, no por un bug puntual): con los pesos default, Warden mitiga ~13% mejor que Base contra el mismo golpe (PuntosDefensa 69.6 vs 65.6) — funciona como se diseñó, no se encontró inconsistencia en ese escenario específico |
| 2026-06-26 | **Confirmado: la fórmula no depende de Nexo** — ítems 100% vanilla craftados/encantados a mano pasan por el mismo camino (`DamageModifier.BASE`, `Attribute.ARMOR`/`ARMOR_TOUGHNESS`, `Enchantment.PROTECTION`) que los ítems de Nexo. `NexoHook` solo interviene en el override opcional de Fase 7 — ver §6.1.2 |
| 2026-06-26 | **Auditoría de rendimiento/escalabilidad para 100 jugadores** (§12.1): sin scheduler, sin estado por jugador, sin I/O en el hot path. Se cachearon los hooks en `CombatDamageListener` (antes 2 `HashMap.get()` por golpe) y se agregó short-circuit en `EquipmentReader.sumProtectionLevels` para no llamar a la API de Nexo cuando no hay overrides configurados — ninguno de los dos cambia el resultado, verificado con los 9 tests |
| 2026-06-26 | **Bug encontrado y corregido (dueño de server):** `minimum-damage-per-hit` se aplicaba también a mobs vanilla (Zombie, Warden) y a mobs/skills de MythicMobs como atacantes, subiendo su daño al piso de seguridad cuando no debería tocarlos en absoluto. Fix: `CombatDamageListener` ahora exige `event.getDamager() instanceof Player` antes de leer `BASE` o tocar cualquier modifier — ver §6.1.3 |
| 2026-06-26 | **MythicMobs `ignorearmor` (true damage) respetado incluso con atacante jugador:** nuevo `MythicTrueDamageListener` escucha `MythicDamageEvent` (evento propio de MythicMobs, no `EntityDamageByEntityEvent`) en prioridad `MONITOR`, lee `DamageMetadata.getIgnoresArmor()` y marca al defensor en un `Set<UUID>` con limpieza programada a un tick como red de seguridad. `CombatDamageListener` consume esa marca antes de aplicar la fórmula. Firmas de API verificadas con `javap` contra `Mythic-Dist-5.12.1.jar`, no asumidas — ver §6.1.3/§9.3 |
| 2026-06-26 | **Power y soporte de proyectiles eliminados por completo** (pedido explícito del dueño del server: "que se comporten como Minecraft lo hace, sin que nosotros tengamos algo que ver con eso"): se borró `scope.apply-to: PROJECTILE`, la sección `power:` de `config.yml`, `DamageFormula.powerBonus`, y los campos `powerUseVanillaFormula`/`powerBonusPerLevel` de `DamageFormulaConfig` (y su test). Arcos/ballestas ya no tienen ninguna ruta de código en InfyDamage — ver §6.1.3 |
