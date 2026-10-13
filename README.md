# CapitalCraft (Fabric 1.20.1)

El capitalismo en Minecraft: casino con el **Mejorador de Ítems**, **bolsa + 12 criptomonedas**, **bloques GPU** para minar y un sistema de **jornadas laborales** que te quita casi todo al acabar el día.

## Cómo compilarlo en GitHub (sin instalar nada)
1. Crea un repositorio vacío en GitHub y sube **todo el contenido** de esta carpeta (incluida `.github/`).
2. Ve a la pestaña **Actions** → workflow **build** (se lanza solo con cada push, o con *Run workflow*).
3. Cuando termine, abre la ejecución y descarga el artefacto **CapitalCraft-1.20.1** → dentro está `capitalcraft-1.0.0.jar`.
4. Copia el `.jar` a tu carpeta `mods` junto a **Fabric Loader 0.15+** y **Fabric API 0.92.2+1.20.1**. Java 17.

Si el build falla, copia el error de la pestaña Actions y se corrige.

## Qué hay dentro
| Cosa | Cómo se usa |
|---|---|
| **Ficha de Casino** | Clic derecho. Apuesta cualquier ítem (o monedas) contra cualquier ítem del juego. La casa se queda el 10 %. |
| **Terminal Bursátil** | Clic derecho. 12 acciones con noticias y dividendos diarios + 12 criptomonedas, gráfico en vivo, cartera, comisión 0,5 %. |
| **GPU Básica / Avanzada / Élite** | Colócala y clic derecho: elige moneda, overclock y apágala/enciéndela. Paga la luz; refrígerala con agua o hielo al lado. |
| **Jornadas** | Cada día de juego te asignan 1 de 70 trabajos con su kit. Al atardecer te pagan, y la empresa confisca todo menos comida, antorchas y una cama. Lo guardado en cofres (y las cajas shulker) no se toca. |

Comandos: `/capital saldo`, `/capital jornada`, `/capital ayuda`. Para operadores: `dar`, `quitar`, `trabajo <jugador> <id>`, `finjornada <jugador>`, `noticia`.
Tecla **J**: muestra/oculta el panel de jornada.

Configuración en `config/capitalcraft.json` (ventaja de la casa, hora de fin de jornada, confiscación, comisiones, minería…).
Los trabajos son JSON en `data/capitalcraft/capitalcraft_jobs/`, así que se pueden añadir más con un datapack.

Todas las texturas, sonidos, modelos, recetas y traducciones (es/en) se generan con `python3 tools/generate_assets.py`.
