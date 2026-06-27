# InfyDamage — Plan de implementación

> Checklist de ejecución derivado de `CONTEXT.md`. Cada fase es un commit/PR razonable. No arrancar una fase sin haber cerrado la anterior, salvo que se indique lo contrario.

---

## Fase 0 — Setup del proyecto ✅

- [x] `pom.xml`: `groupId=me.gadisplay`, `artifactId=InfyDamage`, Java 21, `paper-api` `1.21.11-R0.1-SNAPSHOT` (confirmado contra el `maven-metadata.xml` real de PaperMC).
- [x] Repositorios ya conocidos (calcados de `InfyContratos/pom.xml`): PaperMC, Nexo (`https://repo.nexomc.com/releases`), MythicMobs (`https://mvn.lumine.io/repository/maven-public/`).
- [x] **THEnchantments resuelto:** no necesita Maven dependency ni hook. Es un plugin del workspace (`C:\Users\PC\Desktop\InfyPlugins\ThEnchantments`) que aplica los niveles extendidos con `meta.addEnchant(enchantment, level, true)` — escribe el nivel real en el dato vanilla estándar. InfyDamage lee ese nivel con `ItemStack#getEnchantmentLevel` de la API normal de Bukkit.
- [ ] **MythicCrucible BLOQUEADO:** el POM publicado por Lumine (`io.lumine:MythicCrucible`, probado en 2.2.0 y 2.1.0) tiene un `<parent>` roto (`io.lumine:MythicCrucible-Plugin` con property `${mythiccrucible.version}` sin resolver — bug de publicación de Lumine, no se arregla bajando de versión). Decisión: avanzar sin esta dependencia por ahora; el hook de MythicCrucible queda bloqueado hasta tener el JAR real para instalar local vía `mvn install:install-file` (mismo patrón que InfyCodex en InfyContratos).
- [x] `maven-shade-plugin` **no agregado** — hoy ninguna dependencia necesita shading, todo es `provided`. Se agrega en la fase que lo necesite.
- [x] Paquete base: `me.gadisplay.infydamage`, con subpaquetes `config`, `formula`, `hook/impl`, `command`, `listener` ya creados (vacíos, listos para Fases 1-5).
- [x] Clase principal `InfyDamage extends JavaPlugin` (`src/main/java/me/gadisplay/infydamage/InfyDamage.java`) — skeleton con `onEnable`/`onDisable` y el orden de carga documentado en comentario.
- [x] `plugin.yml`: `softdepend: [Nexo, THEnchantments, MythicMobs, MythicCrucible]`, comando `infydamage`, permiso `infydamage.admin`.
- [x] Maven Wrapper (`mvnw`/`mvnw.cmd`/`.mvn/wrapper`) copiado de InfyContratos — el sistema no tiene Maven instalado globalmente, así que el build depende del wrapper.
- [x] **Verificado:** `./mvnw compile` corre limpio, exit code 0.

## Fase 1 — Config recargable (resuelve §5.4) ✅

- [x] `config.yml` default con el esqueleto de `CONTEXT.md` §7.2 (`combat-pacing`, `formulas`, `sharpness`, `power`, `scope`).
- [x] `DamageFormulaConfig`: `record` inmutable con los valores ya parseados (attenuation, weights, modifiers, mínimos, bonus-per-level de Power, scope como `Set<DamageCause>`). Reload = reemplazar la instancia entera, nunca mutar campos.
- [x] `ConfigManager`: `load()` (saveDefaultConfig + reloadConfig) y `reload()` (reloadConfig + reconstruye `DamageFormulaConfig`). Ningún valor de balance como constante fija en código — todo sale de este objeto. Conectado en `InfyDamage.onEnable()`.
- [x] **Verificado:** `./mvnw compile` corre limpio, exit code 0.

## Fase 2 — Hooks de soft-dependency (calco de `BaseHook`/`HookManager` de THCore) ✅

- [x] `BaseHook` abstracta: `getPluginName()`, `load()`, `tryLoad()`, `isEnabled()`.
- [x] `HookManager`: registro de hooks + `getHook(Class<T>)`. Conectado en `InfyDamage.onEnable()` después de `ConfigManager`.
- [x] `NexoHook`: expone `resolveItemId(ItemStack)` vía `com.nexomc.nexo.api.NexoItems.idFromItem(...)` (firma real confirmada inspeccionando el JAR 1.7.3 con `javap`, no adivinada). Solo hace falta para los overrides de Fase 7 — el daño/armadura del día a día sigue leyendo atributos directo del `ItemStack`.
- [x] `MythicMobsHook`: expone `isMythicMob(Entity)` vía `MythicBukkit.inst().getAPIHelper().isMythicMob(entity)` (firma real confirmada con `javap` sobre `Mythic-Dist-5.12.1.jar`).
- [ ] `MythicCrucibleHook`: identificar daño que debe **saltarse** la fórmula (§6.2 / §9.4). **Sigue bloqueado** — repo Maven roto de MythicCrucible (ver Fase 0).
- [x] THEnchantments **confirmado sin hook propio** — no aplica ninguna acción en esta fase.
- [x] **Verificado:** `./mvnw compile` corre limpio, exit code 0.

## Fase 3 — Motor de fórmula (`DamageFormula`) ✅

- [x] `DamageFormula`: clase pura (sin Bukkit/eventos) con `sharpnessBonus(level)` (curva vanilla, sin tope), `powerBonus(level)` (curva propia), `equipmentDamage(...)`, `defensePoints(armor, toughness, protectionSum)` y `mitigatedDamage(equipmentDamage, defensePoints)` con el piso de `minimum-damage-per-hit`.
- [x] La **lectura real** de `GENERIC_ATTACK_DAMAGE`/`GENERIC_ARMOR`/`GENERIC_ARMOR_TOUGHNESS`/niveles de encantamiento desde `ItemStack`/`Player` queda para la Fase 4 (listener) — a propósito, para poder testear la matemática sin un servidor levantado.
- [x] JUnit 5 agregado (`scope=test`) + `maven-surefire-plugin` explícito.
- [x] 6 tests de regresión, incluyendo el caso de §7.3 recalibrado (ver historial de decisiones) que confirma que el piso de seguridad funciona cuando el combo endgame lo pisa.
- [x] **Verificado:** `./mvnw test` → 6/6 tests pasan, exit code 0.

## Fase 4 — Listener de combate (resuelve el riesgo de §5.3) ✅

- [x] `CombatDamageListener`: `EntityDamageByEntityEvent` en prioridad `HIGH`, filtrado por `config.scopeApplyTo()` (`ENTITY_ATTACK`/`PROJECTILE`).
- [x] **Corrección de diseño encontrada al implementar (ver historial de decisiones):** NO se setea `DamageModifier.BASE` con un cálculo propio. Se lee `event.getDamage(DamageModifier.BASE)` (vanilla, ya correcto) como `DañoEquipo`, y solo se reemplazan `ARMOR` (→ 0) y `MAGIC` (→ `mitigatedDamage - baseDamage`). Esto preserva Fuerza/Debilidad/críticos automáticamente, sin reimplementarlos.
- [x] Setters siempre con modifier explícito (`setDamage(DamageModifier, double)`), nunca el genérico de un argumento — aplicado tal como exige la nota técnica de `CONTEXT.md` §5.3.
- [x] **Variante de proyectiles resuelta sin ambigüedad:** el mismo código sirve para melee y flechas — no hace falta distinguir tipo de arma ni leer `AbstractArrow#getWeapon()`, porque `DamageModifier.BASE` ya viene correcto para ambos casos directo del evento.
- [x] Nombres de atributo corregidos a la API real de 1.21+: `Attribute.ARMOR`, `Attribute.ARMOR_TOUGHNESS` (sin prefijo `GENERIC_`).
- [ ] Bypass total si `MythicCrucibleHook` marca la fuente como pico intencional — sigue bloqueado (MythicCrucible no compila, ver Fase 0/2).
- [ ] Flag/metadata para doble procesamiento con otro plugin de combate: **no se implementó como código.** Es un problema de coexistencia entre plugins que una metadata interna no puede prevenir de verdad (no podemos controlar qué hace otro plugin con el mismo evento) — queda como nota operativa: no instalar dos plugins de "daño custom" al mismo tiempo en el mismo servidor, no como guard en runtime.
- [x] **Verificado:** `./mvnw test` corre limpio (compila + 6/6 tests siguen pasando), exit code 0.

### Enmienda post-cierre (pedido del dueño del server): umbral mínimo de daño

- [x] **Bug encontrado:** `minimum-damage-per-hit` subía artificialmente golpes de puño vacío/manzana/proyectiles sin fuerza, porque la fórmula no distinguía si el atacante pegó con un arma real. Fix: `scope.weapon-damage-threshold` (default `1.0`, base vanilla de puño) gatea sobre `DamageModifier.BASE` apenas se lee, antes de cualquier chequeo de MythicMobs o `setDamage(...)` — ver `CONTEXT.md` §6.1.1.
- [x] Se eligió umbral de daño (no lista de Materials/IDs) porque cubre proyectiles automáticamente y no depende de mantener una lista cada vez que Nexo agregue un arma nueva.
- [x] Confirmado (auditoría pedida por el dueño del server): la fórmula no depende de Nexo en ningún punto del camino principal — ítems vanilla craftados/encantados a mano pasan por el mismo cálculo. Ver `CONTEXT.md` §6.1.2.
- [x] **Verificado:** `./mvnw test` → 9/9 tests siguen pasando tras el cambio, exit code 0.

### Enmienda post-cierre (pedido del dueño del server): atacante debe ser Player, true damage de MythicMobs, eliminación de Power/proyectiles

- [x] **Bug encontrado:** `minimum-damage-per-hit` subía el daño de mobs vanilla (Zombie, Warden) y de mobs/skills de MythicMobs como atacantes, cuando esos golpes tienen que ser 100% vanilla/Mythic. Fix: `CombatDamageListener` exige `event.getDamager() instanceof Player` antes de leer `BASE` o tocar cualquier modifier (CONTEXT.md §6.1.3).
- [x] `isExcludedMythicSource` renombrado a `isExcludedMythicDefender` y simplificado para chequear solo al defensor — el atacante ya está garantizado `Player` en ese punto del listener.
- [x] Nuevo `MythicTrueDamageListener` (`listener/MythicTrueDamageListener.java`): escucha `MythicDamageEvent` en prioridad `MONITOR`, lee `DamageMetadata.getIgnoresArmor()` (firma confirmada con `javap` contra `Mythic-Dist-5.12.1.jar`) y marca al defensor en un `Set<UUID>` (`ConcurrentHashMap.newKeySet()`) con limpieza programada a un tick como red de seguridad. `CombatDamageListener.consumeTrueDamage(UUID)` la consume antes de aplicar la fórmula.
- [x] `InfyDamage.onEnable()`: `MythicTrueDamageListener` solo se instancia/registra si `hookManager.getHook(MythicMobsHook.class) != null` — evita `NoClassDefFoundError` por la clase `MythicDamageEvent` cuando MythicMobs no está instalado (mismo patrón de guard que el resto de las soft-dependencies).
- [x] **Power y `PROJECTILE` eliminados por completo** (pedido explícito: "que se comporten como Minecraft lo hace, sin que nosotros tengamos algo que ver con eso"): removidos `scope.apply-to: PROJECTILE` y la sección `power:` de `config.yml`; `DamageFormula.powerBonus`, `DamageFormulaConfig.powerUseVanillaFormula`/`powerBonusPerLevel` y el test `powerUsesItsOwnConfigurableCurve` borrados del código.
- [x] **Verificado:** `./mvnw test` → 8/8 tests pasan (9 menos el test de Power), exit code 0. `./mvnw package` compila limpio, exit code 0.

## Fase 5 — Framework de comandos (calco de `SubCommand`/`BaseCommand` de THCore) ✅

- [x] `SubCommand` (interfaz): `getName()`, `getPermission()`, `execute(sender, args)`, `tabComplete(sender, args)` — firmas verificadas leyendo el `SubCommand.java`/`BaseCommand.java` reales de THCore, no el resumen de memoria de un agente anterior.
- [x] `BaseCommand` (abstracta): mapa `LinkedHashMap` de subcomandos, validación de permisos, `onNoArgs()`. Simplificada respecto a THCore: sin `MessageManager` propio (no existe en InfyDamage), mensajes en texto plano con `ChatColor`.
- [x] `InfyDamageCommand extends BaseCommand`, registrado en `InfyDamage.onEnable()` contra el comando `infydamage` ya declarado en `plugin.yml`.
- [x] Subcomando `reload`: llama a `ConfigManager.reload()` y confirma por chat. Permiso `infydamage.admin`.
- [x] **Verificado:** `./mvnw test` corre limpio, exit code 0.

## Fase 6 — Comando `simulate` (pendiente de §12, prioridad alta para calibrar sin pegarle a nadie) ✅

- [x] Subcomando `simulate <atacante> <defensor>`: solo nombres de jugador conectado por ahora. **Scope recortado:** el fallback de valores manuales por flags (ej. `--armor=28`) no se implementó — agrega un parser de argumentos no trivial y el caso de uso real (comparar sets ya equipados, como Vacío vs Dragón) no lo necesita. Se puede agregar después si hace falta probar stats hipotéticos sin tener el ítem.
- [x] Output: desglose paso a paso — `DañoEquipo` (con el ATTACK_DAMAGE y el bonus de Filo por separado), `PuntosDefensa` (Armadura/Toughness/Protección por separado) y `DañoFinal` mitigado.
- [x] **Actualizado tras la enmienda de Fase 4:** `DamageSimulationResult` ahora incluye `appliesFormula` (mismo umbral `scope.weapon-damage-threshold`, comparado contra `equipmentDamage`). Si el atacante simulado no tiene un arma reconocida, el comando muestra un mensaje claro en vez de un desglose de mitigación que no aplicaría en juego.
- [x] `DamageSimulator`/`DamageSimulationResult` en el paquete `formula`: recalculan a mano lo que el listener real lee directo del evento (`DamageModifier.BASE`), porque `simulate` no tiene un evento del cual leer ese valor.
- [x] `EquipmentReader` extraído como utilidad compartida entre `CombatDamageListener` y `DamageSimulator` — evita duplicar la lectura de atributos/encantamientos en los dos lugares.
- [x] **Verificado:** `./mvnw test` corre limpio, exit code 0. Falta probarlo en un server real con los sets Vacío/Dragón (no hay servidor levantado en esta sesión).

## Fase 7 — Casos híbridos y overrides (diseño fase 2, §12) ✅ (parcial)

- [x] Overrides por `nexo:item_id`: `overrides.protection-cap-by-item-id` en `config.yml`, leído en `DamageFormulaConfig`. `EquipmentReader.sumProtectionLevels(entity, nexoHook, capsMap)` recorta el nivel real de Protección de una pieza específica al cap configurado antes de sumarlo — resuelve el caso híbrido de §3.1 (Netherite + Protección X) sin tocar el ítem de Nexo.
- [x] **Cap por tier de ítem fusionado con el bullet anterior:** en vez de construir dos mecanismos separados (uno "por ítem" y otro "por tier"), un solo mapa por `item_id` cubre ambos casos — un admin lista cada ítem híbrido explícitamente. Evita una segunda abstracción redundante.
- [x] Whitelist/blacklist de fuentes MythicMobs: `mythicmobs-scope.mode` (`NONE`/`WHITELIST`/`BLACKLIST`) + `mob-types` en `config.yml`. `MythicMobsScopeMode.allows(...)` (con 3 tests propios) decide si el golpe se excluye de la fórmula custom cuando el atacante o el defensor es un mob Mythic de un tipo configurado. Usa `ActiveMob#getMobType()` (firma real confirmada con `javap`).
- [ ] **MythicCrucible sigue sin cubrir** — sigue bloqueado por el repo Maven roto (Fase 0/2). El whitelist/blacklist de MythicMobs no resuelve nada de Crucible.
- [x] **Verificado:** `./mvnw test` → 9/9 tests pasan, exit code 0.

## Fase 8 — Calibración de balance

- [ ] Usar `simulate` con los sets reales del servidor (Vacío, Dragón, y los próximos tiers) para ajustar `attenuation-factor`, `protection-weight`, `toughness-weight`, `power.bonus-per-level`.
- [ ] Confirmar en juego (no solo en `simulate`) que Filo 10 vs Filo 5 y +1 punto de daño en un arma Nexo generan una diferencia perceptible contra el mismo defensor.

## Fase 9 — Pendientes abiertos (no bloquean el lanzamiento inicial)

- [ ] Secciones `pvp` / `pve` con multiplicadores distintos (§10).
- [ ] API pública para que otros plugins Infy consulten el daño calculado.
- [ ] Documentación operativa en `docs/` (comandos, permisos, API).

---

## Orden recomendado de implementación

```
Fase 0 (setup) → Fase 1 (config) → Fase 3 (fórmula pura, con tests)
→ Fase 2 (hooks) → Fase 4 (listener) → Fase 5 (comandos) → Fase 6 (simulate)
→ Fase 8 (calibración) → Fase 7 (overrides) → Fase 9 (pendientes)
```

La Fase 3 (fórmula) va antes que la Fase 4 (listener) a propósito: conviene tener la matemática testeada en aislado antes de conectarla al evento de Bukkit, donde los bugs son mucho más difíciles de diagnosticar.
