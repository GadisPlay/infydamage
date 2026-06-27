# InfyDamage

Plugin para **Paper 1.21+** (Java 21) que reemplaza la mitigación vanilla de armadura y protección por una **fórmula configurable**, pensado para servidores con progresión de equipo custom (Nexo, THEnchantments, MythicMobs).

En Minecraft vanilla, la armadura se congela en **20 puntos** y la Protección en **EPF 20**. Sets endgame con más armadura o Protección VII–X no se sienten en combate. InfyDamage intercepta el daño en runtime, lee el equipamiento real y aplica una curva exponencial calibrable.

## Requisitos

| Componente | Versión |
|------------|---------|
| Servidor | Paper **1.21.1+** (probado con API 1.21.11) |
| Java | **21** |

## Dependencias opcionales (soft)

| Plugin | Función |
|--------|---------|
| **Nexo** | Atributos custom de armadura, daño y dureza en ítems |
| **THEnchantments** | Niveles extendidos de Filo/Protección (se leen vía API vanilla de Bukkit) |
| **MythicMobs** | Scope por tipo de mob y soporte de *true damage* (`ignoresArmor`) |
| **MythicCrucible** | Reservado para futuras integraciones (hook pendiente) |

Sin Nexo ni THEnchantments el plugin sigue funcionando con ítems vanilla.

## Instalación

1. Compila el JAR:

```bash
mvn clean package
```

2. Copia `target/InfyDamage-0.1.0-SNAPSHOT.jar` a la carpeta `plugins/` del servidor.
3. Reinicia (o recarga) el servidor.
4. Ajusta `plugins/InfyDamage/config.yml` y recarga con `/infydamage reload`.

## Comandos y permisos

| Comando | Descripción | Permiso |
|---------|-------------|---------|
| `/infydamage reload` | Recarga `config.yml` en caliente | `infydamage.admin` |
| `/infydamage simulate <atacante> <defensor>` | Simula un golpe entre dos jugadores conectados sin aplicar daño | `infydamage.admin` |

`infydamage.admin` tiene `default: op`.

## Qué modifica y qué no

### Intercepta (con la fórmula custom)

- Golpes **cuerpo a cuerpo** (`ENTITY_ATTACK`) donde el **atacante es un jugador**.
- Armas cuyo daño base supera `weapon-damage-threshold` (por defecto `1.0`; puño/manzana quedan fuera).

### No toca (100 % vanilla)

- Mobs pegando (Zombie, Warden, MythicMobs como atacante).
- Arcos, ballestas y proyectiles.
- Daño ambiental (caída, fuego, lava, veneno…).
- Modificadores que vanilla aplica después: críticos, Fuerza/Debilidad, resistencia, absorción, bloqueo.

### Fórmula

```
PuntosDefensa = Armadura + (Toughness × toughness-weight) + (ΣProtección × protection-weight)
DañoFinal     = DañoEquipo × (attenuation ^ PuntosDefensa) × global-damage-modifier
```

- `DañoEquipo` viene del modifier `BASE` de vanilla (atributos Nexo + Filo ya incluidos).
- Se anulan los modifiers `ARMOR` y `MAGIC` de vanilla y se sustituyen por la mitigación custom.
- `minimum-damage-per-hit` evita golpes de 0 en combates endgame.

Parámetros principales en `config.yml`:

| Clave | Efecto |
|-------|--------|
| `global-damage-modifier` | Multiplicador global del daño final |
| `attenuation-factor` | Qué tan rápido escala la defensa (más cerca de 1.0 = menos mitigación por punto) |
| `protection-weight` | Peso de los niveles de Protección |
| `toughness-weight` | Peso de ARMOR_TOUGHNESS (dureza Nexo) |
| `weapon-damage-threshold` | Umbral mínimo de daño base para activar la fórmula |
| `overrides.protection-cap-by-item-id` | Cap de Protección efectiva por ID Nexo (casos híbridos) |
| `mythicmobs-scope` | Whitelist/blacklist de mobs Mythic como defensor |

## Desinstalación — ¿queda algo permanente?

**No.** InfyDamage no modifica el mundo, inventarios, atributos de jugadores ni archivos de otros plugins.

- Solo escucha eventos de daño mientras está cargado.
- Al quitar el JAR y reiniciar (o `/reload confirm` si usas reload), el servidor vuelve a la mitigación **100 % vanilla**.
- Lo único que queda en disco es la carpeta `plugins/InfyDamage/` con `config.yml`; puedes borrarla si no vas a reinstalar.

No hay base de datos ni migraciones. No hay cambios persistentes en entidades ni en ítems.

## Desarrollo

```bash
mvn clean compile   # compilación rápida
mvn test            # tests unitarios de la fórmula (sin servidor)
mvn clean package   # JAR final
```

Documentación de diseño interna: [`CONTEXT.md`](CONTEXT.md) y [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md).

## Licencia

Sin licencia explícita por ahora — uso privado del ecosistema Infinity / GadisPlay.
