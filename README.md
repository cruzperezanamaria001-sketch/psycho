# Psycho Village (Forge 1.20.1)

Mod independiente para Minecraft Java 1.20.1, Forge 47.4.10 y Java 17.

## Compilar
1. Instala Java 17 (JDK) y descarga/instala Forge 1.20.1-47.4.10.
2. Extrae el ZIP.
3. En la carpeta del proyecto ejecuta:
   - Windows: `gradlew.bat build`
   - Linux/macOS: `./gradlew build`
4. El JAR estará en `build/libs/PsychoVillage-1.0.0.jar`.
5. Copia el JAR a `.minecraft/mods`.

## Comandos
- `/psycho spawn` — aparece un aldeano asesino cerca de ti.
- `/psycho kill` — mata al aldeano más cercano y deja un cuerpo decorativo.
- `/psycho attack` — fuerza al asesino cercano a atacar.
- `/psycho window attack` — intenta romper un cristal cercano y atacar.\n\n## Funciones añadidas en esta actualización\n- Los aldeanos reciben nombres.\n- El asesino puede abrir puertas cercanas.\n- Los asesinatos reproducen un sonido de aldeano herido, alertan a jugadores cercanos y dejan efectos visuales.\n- A veces el cuerpo queda oculto cerca/debajo de una cama.\n- Ocasionalmente aparece una pista en papel con nombres de aldeanos cercanos.\n- El asesino puede atacar a jugadores cercanos durante la noche.

## Nota
Este sigue siendo un prototipo. Los cuerpos son armor stands decorativos y el sonido de alerta usa audio vanilla. La secuencia de ventana es una versión sencilla: busca y rompe un cristal cercano, teletransporta al asesino y luego ataca. La secuencia de ventana incluye mirar, agacharse, romper el cristal y entrar. El cuerpo usa una cabeza con el nombre del jugador; no copia exactamente su skin completa.
