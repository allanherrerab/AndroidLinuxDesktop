# Debian Noroot
[Read in English](README.md)
> Pon un escritorio Debian en tu teléfono Android. Sin root. La paciencia se vende por separado.

Ejecuta Debian sobre Android con un solo clic. Debería funcionar en casi cualquier dispositivo. Desata un entorno de escritorio completo y sin restricciones en tu dispositivo móvil. Frustración instantánea garantizada (a menos que uses un ratón o un lápiz óptico).

XFCE, Firefox, una terminal, un gestor de archivos y sonido de verdad, todo metido en una app de Android. Abre ventanas. Instala paquetes. Pasa diez minutos intentando hacer clic en algo que claramente fue diseñado para un ratón. Es Linux de escritorio en tu bolsillo, con la mayoría de las decisiones cuestionables que eso implica.

Reconstruí esto sobre Debian Trixie, inspirado por [Debian Noroot de pelya](https://github.com/pelya/debian-noroot) y usando el APK original de la era de Android 4 como referencia. El rey ha vuelto.

Espera algunos problemas. Las cosas pueden ir lentas, resultar incómodas, romperse en tu teléfono en particular, o simplemente ser incomodas de manejar con el dedo. A veces tendrás una pequeña estación de trabajo Linux útil. A veces tendrás un pasatiempo de resolución de problemas. Ambas cosas pueden ser divertidas, siempre que no contaras con esto para salvarte la fecha de entrega.
Este es un proyecto independiente, no un lanzamiento oficial de Debian.org. **No** es un SO Debian completo. Es una capa de compatibilidad que te permite ejecutar aplicaciones Debian. Comparte el kernel de Android, no rootea tu teléfono y no lo convierte en una máquina Debian que arranca normalmente.

## Contenido

- [Lo que obtienes](#lo-que-obtienes)
- [Antes de invitar a Debian a tu teléfono](#antes-de-invitar-a-debian-a-tu-teléfono)
- [Instalar y arrancar](#instalar-y-arrancar)
  - [Instalar con ADB](#instalar-con-adb)
  - [Compilar e instalar una actualización](#compilar-e-instalar-una-actualización)
- [Manejar un escritorio con los pulgares](#manejar-un-escritorio-con-los-pulgares)
- [Cómo funciona este artefacto](#cómo-funciona-este-artefacto)
- [Compilar desde el código fuente](#compilar-desde-el-código-fuente)
  - [Primera compilación](#primera-compilación)
  - [Los comandos de Make](#los-comandos-de-make)
- [Dónde vive cada cosa](#dónde-vive-cada-cosa)
- [Comprobar que realmente funciona](#comprobar-que-realmente-funciona)
- [Se rompió. ¿Y ahora qué?](#se-rompió-y-ahora-qué)
- [Gracias, y el papeleo](#gracias-y-el-papeleo)

## Lo que obtienes
- **Un escritorio Linux de verdad.** El motor gráfico X11 está integrado. Sin app de servidor X separada que instalar y convencer de que coopere.
- **Sonido.** PulseAudio alimenta la API AudioTrack de Android. Las teclas de volumen del teléfono, el volumen de la app y los controles de volumen de XFCE funcionan. Sí, los deslizadores de volumen extra son intencionales. Siéntete libre de silenciar tu micrófono, ya que esta app también puede usarlo.
- **Controles de pantalla.** Presets de resolución y tamaños personalizados, escalado ajustar/estirar/entero/personalizado, filtrado, orientación, pantalla completa y zoom del 100–400%. Los botones pequeños del escritorio siguen siendo botones pequeños del escritorio, pero tienes opciones.
- **Controles de ratón y teclado.** Touchpad relativo o entrada táctil absoluta, bloqueo de arrastre, clic derecho/central, desplazamiento con dos dedos, modificadores, F1–F12, reasignación de teclas de hardware y pegado del portapapeles. Se incluyen ratón/lápiz óptico físico y controles por giroscopio; la cobertura de pruebas de hardware sigue siendo limitada.
- **Configuración regional.** Elige los idiomas de la app y de Debian por separado, más la distribución del teclado invitado y la zona horaria. Se incluyen inglés, español, portugués brasileño, francés, alemán, japonés y polaco, junto con locales UTF-8, traducciones de XFCE/Firefox y fuentes japonesas.
- **Un APK pequeño y una imagen Debian mucho menos pequeña.** La app pesa unos 2 MB; la imagen unos 324 MiB. El Linux de escritorio no se convirtió de repente en pelusa de bolsillo.
Consulta [PARITY.md](PARITY.md) para ver qué coincide con la app antigua, qué cambió y qué opciones antiguas no han llegado al corte.

## Antes de invitar a Debian a tu teléfono
Tal como están las cosas, necesitarás un **dispositivo Android ARM de 64 bits con Android 8.0 o superior**. La compilación actual apunta a Android 16/API 36. No se incluyen otras arquitecturas de CPU, pero deberían funcionar siempre que crees un tarball de rootfs para ellas. También necesitarás suficiente almacenamiento interno para el archivo importado, el sistema extraído y los archivos temporales de extracción. El escritorio instalado ocupa considerablemente más espacio que la descarga (alrededor de 1.5 GB en total).
**Hay varias limitaciones**: algunas heredadas de la app original, otras corregidas como mejoras:
- **No puede estropear tu dispositivo Android**, porque es una app Android normal y bien portada, que no necesita acceso root.
- **Sin acceso root de Android.** `sudo` dentro de Debian usa la identidad root emulada de PRoot. Puede instalar paquetes, pero no habilitará el modo monitor en tu tarjeta Wi-Fi de Android ni rooteará tu dispositivo.
- **Soporte de audio mejorado.** La app original no tenía audio funcional; la compilación actual enruta PulseAudio hacia la API AudioTrack de Android. Las teclas de volumen del teléfono, el volumen de la app y los controles de volumen de XFCE funcionan todos. Consulta [Lo que obtienes](#lo-que-obtienes).
- **Sin soporte OpenGL.** La GPU de Android presenta el escritorio, pero las aplicaciones Linux invitadas no obtienen VirGL ni OpenGL acelerado por hardware. Ajusta tus ambiciones de juego en consecuencia (Minecraft Java funcionará, GTA V ciertamente no).
- **Sin acceso privilegiado al hardware.** Debian comparte el kernel del teléfono. No puedes reparticionar la tarjeta SD, grabar CD-ROM, ejecutar `ping` en crudo ni olfatear tu red, etc.
- **Sin capacidad de mover la app a la tarjeta SD**, así que necesitarás mucho almacenamiento interno.
- **Los servidores que requieren puertos inferiores a 1024 no arrancarán**, porque todos necesitan funciones root. Siéntete libre de arrancar Apache en el puerto 8080 en su lugar.
- **Sin captura de micrófono.** Puede que a veces no funcione.
- **Android elige el teclado de Android.** La configuración de distribución de la app cambia el teclado X11 invitado. La entrada Unicode japonesa funciona, pero no se configura un método de entrada japonés separado dentro de Debian.
He probado en un Galaxy S24 FE (SM-S721B), Android 16. Los binarios nativos pasan las comprobaciones de alineación de 16 KB, pero las pruebas en un dispositivo con páginas de 16 KB siguen pendientes. Un teléfono que funciona es alentador; no es compatibilidad universal con Android. [Process.md](Process.md) tiene los resultados de las pruebas y los límites.

## Instalar y arrancar
Necesitarás estos dos archivos de la misma compilación:

| Archivo | Qué hace |
| --- | --- |
| `dist/debian-noroot-debug.apk` | La app de desarrollo |
| `dist/main.2.org.debiannoroot.obb` | La imagen del sistema de archivos Debian |

Estos son resultados de compilación locales, no enlaces a un lanzamiento alojado. Si lo estás compilando tú mismo, salta a [Compilar desde el código fuente](#compilar-desde-el-código-fuente).
1. Instala el APK y copia el OBB correspondiente en la carpeta **Downloads** del teléfono.
2. Abre Debian Noroot, elige tus idiomas, la distribución del teclado invitado y la zona horaria, luego selecciona **Guardar y continuar**.
3. Selecciona **Importar imagen Debian** y elige el OBB. También se acepta el `debian-rootfs.tar.xz` correspondiente.
4. Selecciona **Iniciar Debian**. El primer arranque verifica y extrae la imagen. Dale tiempo. Los arranques posteriores reutilizan el escritorio instalado.
El selector de archivos concede acceso a la imagen seleccionada, así que no hay un permiso amplio de almacenamiento de "por favor déjame leerlo todo" que buscar. La app también puede detectar la imagen correspondiente en su directorio OBB de Android. Una imagen faltante o que no coincide recibe un mensaje de recuperación; no obtiene un pase libre al extractor.

### Instalar con ADB
Necesitarás las herramientas de desarrollo de Android para esto, lo cual está fuera del alcance de esta guía. En tu teléfono, habilita **Opciones de desarrollador → Depuración USB**, conecta el teléfono y acepta su solicitud de autorización de depuración. Desde la raíz del repositorio, después de compilar:
```
adb devices -l
 # Si hay varios dispositivos conectados, selecciona el tuyo:
 # export ANDROID_SERIAL=YOUR_DEVICE_SERIAL
 
 adb install -r dist/debian-noroot-debug.apk
 adb push dist/main.2.org.debiannoroot.obb /sdcard/Download/
 adb shell am start -n org.debiannoroot/.MainActivity
 
```
Luego selecciona la imagen transferida en la app y sigue los pasos de configuración anteriores. ADB pone el archivo en el teléfono; tú todavía lo eliges en el importador.

### Compilar e instalar una actualización
Una vez que las dependencias de compilación y la preparación inicial estén en su lugar, esta es la rutina de actualización habitual:
```
. tools/env.sh  # Opcional: usa las herramientas en caché de este espacio de trabajo.
 make app DEBUG=1  # O: make debug.
 
 adb devices -l
 # export ANDROID_SERIAL=YOUR_DEVICE_SERIAL  # Si es necesario.
 
 # Guarda el trabajo del invitado y elige Detener sesión en la app primero.
 adb install -r dist/debian-noroot-debug.apk
 adb shell am start -n org.debiannoroot/.MainActivity
 
```
Selecciona **Iniciar Debian** para usar tu instalación existente. Una actualización ordinaria del APK no necesita otra transferencia de OBB ni importación de rootfs. Los archivos Debian, tu directorio home y la configuración guardada permanecen en su lugar.
**Mantén la misma clave de firma entre actualizaciones.** No desinstales la app ni borres sus datos para actualizarla; esa es una forma muy eficiente de convertir "conservar mis archivos" en "¿dónde se fueron mis archivos?".
Las instalaciones más antiguas añaden automáticamente los paquetes de audio e idioma faltantes cuando es necesario. Eso requiere acceso a Internet; las imágenes nuevas ya los contienen. Consulta **Ver registro de sesión** si la configuración se atasca o una descarga falla, luego reinicia la sesión para reintentar.

## Manejar un escritorio con los pulgares
Desliza un dedo para mover el puntero y toca para hacer clic. Doble toque y arrastrar mantiene pulsado el botón izquierdo del ratón; **Arrastrar** te da un enganche explícito. Los botones de la barra de herramientas manejan los clics derecho y central. Dos dedos desplazan.
La barra de herramientas se desplaza horizontalmente. Si un botón parece faltar, intenta desplazarte antes de decidir que lo he ocultado por despecho.
- **Zoom:** ampliación del 100–400%.
- **F1–F12:** abre una fila de teclas de función desplazable separada.
- **Pantalla completa:** oculta las barras de herramientas y las barras del sistema de Android. El botón ☰ trae los controles de vuelta. Tienes permitido escapar de la pantalla completa.
- **Arrastrar / Ctrl / Alt:** muestran una marca de verificación mientras están mantenidos.
- **Volumen:** ajusta el volumen multimedia del teléfono y la ganancia de reproducción de la app. Dentro de Debian, usa el plugin del panel PulseAudio o `pavucontrol` para el volumen y silencio del invitado/ aplicación. Las teclas de volumen de hardware controlan el volumen multimedia del teléfono.
**Ajustes de pantalla, ratón y audio** contiene los modos de entrada, el tiempo de clic, la velocidad/aceleración, las opciones de entrada de hardware, los controles por giroscopio y la apariencia de los atajos. El búfer de audio es de 200 ms por defecto y se puede ajustar entre 20–500 ms.
Puedes reabrir la configuración regional desde la pantalla principal o **Ajustes**. El idioma de la app cambia inmediatamente; el idioma de Debian, el teclado y la zona horaria se aplican en la siguiente sesión. La mayoría de los cambios de pantalla/entrada se aplican al volver de Ajustes. El dibujo de compatibilidad necesita un reinicio de sesión.
Salir de la pantalla del escritorio mantiene la sesión ejecutándose en segundo plano. **Ver registro de sesión** muestra la sesión actual o la más reciente. Iniciar una nueva sesión borra tanto los registros de Debian como los de X11 nativo, así que guarda cualquier cosa útil antes de reiniciar. Reabrir un escritorio en ejecución conserva los registros de esa sesión.

## Cómo funciona este artefacto
El shell gráfico de Debian se lanza usando PRoot, la solución definitiva de virtualización Linux: [http://proot.me/. Luego renderiza a través del motor X11 integrado de la app; una mejora sobre el original, que lanzaba la app separada de servidor X XSDL y requería que la instalaras y configuraras por separado.](http://proot.me/ "http://proot.me/")
```
Aplicación Android
    ├─ PRoot → Debian Trixie → XFCE / Firefox / otras aplicaciones Linux
    ├─ X11 integrado → superficie nativa de Android
    └─ PCM monitor de PulseAudio → socket Unix → API AudioTrack de Android
 
```
La app verifica la imagen del sistema de archivos raíz y la extrae en almacenamiento privado. PRoot maneja la traducción del sistema de archivos y la identidad invitada emulada. X11 se ejecuta en un proceso Android separado con autenticación por sesión y TCP deshabilitado; los sockets de audio también permanecen en almacenamiento privado. El arranque actualiza los ayudantes invitados de la app sin reemplazar tu rootfs instalado ni tu directorio home.
VNC se mantiene para pruebas de regresión explícitas del host. Android usa X11 nativo.
El sandbox anidado de Firefox está deshabilitado por compatibilidad con PRoot; Firefox sigue ejecutándose dentro del sandbox y los privilegios de la app de Android. PRoot en sí no es una VM separada ni una frontera de seguridad del kernel.

## Compilar desde el código fuente
Así que quieres el pasatiempo de resolución de problemas *y* el sistema de compilación. Bienvenido.
Usa Linux con **Python 3.12+**, **JDK 17 o 21**, **Gradle 8.11.1**, **GNU Make**, un **compilador de C**, **binutils**, **cabeceras de desarrollo de libtalloc/liblzma** y **emulación ARM64 en modo usuario de QEMU**. El SDK de Android necesita **Platform 36**, **Build Tools 36.0.0**, **NDK 27.2.12479018** y **CMake 3.22.1**. Configura `ANDROID_SDK_ROOT` en consecuencia. Las herramientas, fuentes y paquetes iniciales necesitan acceso a Internet.
En este espacio de trabajo, `. tools/env.sh` carga las herramientas en caché cuando están disponibles. No instala dependencias faltantes. En una configuración estándar, configura las rutas equivalentes tú mismo.
Para instalar la cadena de herramientas de línea de comandos fijada dentro de este checkout, ejecuta `make tools` (o `make install-tools`). Esto coloca el JDK, el SDK/NDK de Android, CMake, Gradle y las bibliotecas de compilación del host bajo `build/` sin escribir en los directorios del sistema. Usa `make tools TOOLS_FLAGS=--skip-host` o `make tools TOOLS_FLAGS=--skip-android` para instalar solo un lado de la cadena de herramientas. Android Studio no es necesario para este proyecto; el SDK de línea de comandos es suficiente.
**Una mejora sobre la compilación original.** La app original se compilaba con scripts de debootstrap bajo `img/` (`img-debian-buster-arm64-v8a.sh`, `img-debian-buster-x86_64.sh`), una danza de `build.sh` basada en chroot (`mount -o bind`, `chroot`, instalar gcc, volver a ejecutar `build.sh`, `prepare-img-overlay.sh`), y PRoot extraído de los paquetes Bintray de Termux. La compilación actual reemplaza todo eso con un sistema impulsado por Make: la base Debian está fijada en `rootfs.lock.json`, PRoot se compila desde el código fuente mediante `tools/build-host-proot.sh`, y la pila de runtime/X11 se prepara con `tools/build-runtime.py` y `tools/prepare-x11.py`. La antigua lista de dependencias (`autoconf automake debhelper build-essential libtool qemu-user-static debootstrap pxz schroot apt-cacher-ng`) ya no es necesaria, usa la lista de arriba.
### Primera compilación
Desde la raíz del repositorio:
```
. tools/env.sh  # Herramientas opcionales en caché del espacio de trabajo.
 ./tools/build-host-proot.sh
 python3 tools/build-runtime.py
 python3 tools/prepare-x11.py
 make rootfs
 make test
 make app DEBUG=1
 
```
La compilación de la app ejecuta ensamblado, lint, comprobaciones de compatibilidad JNI nativa y de artefactos, luego coloca el APK y el OBB correspondiente en `dist/`. Las compilaciones directas de Gradle necesitan primero el runtime preparado, los metadatos de la imagen y las anotaciones JNI de solo compilación.
### Los comandos de Make
```
make app            # Predeterminado: compila y verifica el APK de depuración y el OBB correspondiente.
 make app DEBUG=1    # Compilación de depuración explícita; atajo: make debug.
 make app RELEASE=1  # Compilación de lanzamiento sin firmar; atajo: make release.
 make rootfs         # Compila el tarball del escritorio Debian y los metadatos de la imagen.
 make test           # Ejecuta las comprobaciones de regresión del host.
 make clean          # Elimina las salidas generadas y los entregables.
 make help           # Muestra los objetivos y opciones.
 
```
`BUILD_TYPE=debug` y `BUILD_TYPE=release` también seleccionan el modo de compilación. No establezcas ambos `DEBUG=1` y `RELEASE=1`; el Makefile objetará, con bastante razón.
La depuración produce `dist/debian-noroot-debug.apk`, firmado con la clave de desarrollo. El lanzamiento ejecuta el mismo tipo de comprobaciones para la variante de lanzamiento y produce `dist/debian-noroot-release-unsigned.apk`. Firma el APK de lanzamiento con tu clave de lanzamiento antes de instalarlo o distribuirlo. "Lanzamiento" es un modo de compilación, no un hada de las firmas. Ambas variantes usan los mismos bytes de imagen Debian.
Usa las [instrucciones de actualización](#compilar-e-instalar-una-actualización) para instalar una compilación de depuración. El punto de entrada del shell, `tools/build-apk.sh`, también acepta `--debug` o `--release`, opcionalmente con `--rebuild-rootfs`.
Para actualizar paquetes en una imagen compilada previamente, o refrescar solo sus recursos invitados:
```
make rootfs ROOTFS_FLAGS="--reuse-rootfs"
 # Para cambios solo en scripts/plantillas invitadas, usa esto en su lugar:
 make rootfs ROOTFS_FLAGS="--reuse-rootfs --overlay-only"
 
 # Publica el APK y el OBB correspondientes a la imagen modificada:
 make app DEBUG=1
 
```
`make app APP_FLAGS="--rebuild-rootfs"` reconstruye la imagen como parte de la compilación de la app. Evita reconstruir todo el escritorio por un cambio solo en la app, a menos que seas particularmente aficionado a ver la salida del progreso.
`make clean` elimina las salidas de app/nativo/generado/pruebas/informes y el APK, la imagen, los manifiestos y las bibliotecas de runtime preparadas. Conserva `.gradle`, las cadenas de herramientas en caché, las capas de imagen, las fuentes fijadas de runtime/X11, el material de reenlazado, las licencias, las referencias y la evidencia de pruebas locales. No toca una instalación en tu teléfono.
La base Debian está fijada en `rootfs.lock.json`. Los paquetes Debian posteriores provienen de repositorios firmados pero no están fijados a una instantánea. El artefacto X11 y su código fuente correspondiente están fijados por checksum. Su URL nocturna upstream puede cambiar; los bytes diferentes se rechazan deliberadamente. Conserva la caché fijada o actualiza y valida ambas fijaciones juntas. "Descargó algo" es un listón bastante bajo para un sistema de compilación.

## Dónde vive cada cosa

| Ubicación | Contenido |
| --- | --- |
| `app/src/main/` | UI de Android, Java/JNI, manifiesto y recursos |
| `assets/rootfs/` | Arranque invitado, ayudantes de actualización y valores predeterminados del escritorio |
| `tools/` | Herramientas de compilación, traducción, verificación y pruebas de humo |
| `tests/` | Comprobaciones de extracción, ciclo de vida, protocolo y configuración |
| `references/` | APK original y su procedencia |
| `licenses/`, `THIRD_PARTY.md`, `licenses.md` | Licencias de componentes y obligaciones de código fuente |
| `build/` | Cachés ignoradas, intermedios y evidencia de pruebas locales |
| `dist/` | APKs ignorados, rootfs/OBB, runtimes y manifiestos |
| `Makefile` | Puntos de entrada de compilación, prueba y limpieza |
| `rootfs.lock.json` | Metadatos de la imagen base Debian fijada |
| `Process.md` | Historial de desarrollo, correcciones y resultados de pruebas |
| `PARITY.md` | Diferencias con la app original |



## Comprobar que realmente funciona
```
make test
 python3 tools/verify-artifacts.py --full-image \
    --apk dist/debian-noroot-debug.apk --obb dist/main.2.org.debiannoroot.obb
 python3 tools/verify-x11-abi.py --build-type debug
 python3 tools/smoke-desktop.py --proot "$PWD/build/host-proot/bin/proot"
 python3 tools/smoke-audio.py --proot "$PWD/build/host-proot/bin/proot"
 
```
Las pruebas de humo del host ejecutan aplicaciones invitadas ARM64 y comprueban el transporte de audio PCM bajo emulación. No demuestran que la superficie de Android, AudioTrack, el ciclo de vida o la entrada física funcionen. Eso necesita un teléfono, preferiblemente uno que alguien esté dispuesto a probar.
Para una emulación más lenta, `smoke-desktop.py --timeout-scale 2` duplica los plazos de preparación sin saltarse aserciones. Las ejecuciones fallidas guardan un fotograma `-failure.png` cuando está disponible. Un `TMPDIR` respaldado por disco evita extraer el rootfs de prueba en un sistema de archivos temporal pequeño respaldado por RAM.
Para comprobaciones del ciclo de vida en un dispositivo desbloqueado con una app de depuración inicializada, guarda primero el trabajo del invitado:
```
python3 tools/smoke-device-lifecycle.py --serial DEVICE_SERIAL
 
```
Esto detiene/reinicia sesiones, comprueba la rotación/retorno, la preservación de rootfs/home y el reemplazo de registros, luego restaura las preferencias de rotación de Android. Los resultados van a `build/device-lifecycle.json`. Para ejecutar un comando invitado aislado sin escribir en la terminal de alguien, usa `tools/device-guest.py --serial DEVICE_SERIAL -- PROGRAM ARGS...`.
Para pruebas de importación/extracción de primera ejecución:
```
gradle :app:assembleAcceptance :app:lintAcceptance
 
```
Esto compila una app de depuración separada, `org.debiannoroot.acceptance`, bajo `build/app/outputs/apk/acceptance/`, con su propio rootfs y preferencias. Su `org.debiannoroot.FocusProbeActivity` solicita enfoque temporal de medios para las comprobaciones de audio. Estos componentes de prueba no se incluyen en el APK normal.

## Se rompió. ¿Y ahora qué?
Empieza con **Ver registro de sesión**. Contiene la salida de arranque de Debian y el registro de X11 nativo. Una advertencia sobre quién es dueño de `/tmp/.X11-unix` no es, por sí sola, evidencia de que el cielo se haya caído.
- **La imagen no se extrae:** comprueba que el APK y la imagen coincidan, y que el almacenamiento interno no esté lleno.
- **Sin sonido:** comprueba el volumen multimedia del teléfono, la ganancia de la app, el silencio del invitado y el progreso de configuración de paquetes. Tres controles de volumen significan tres oportunidades de silencio.
- **Pantalla negra o rendimiento miserable:** [Process.md registra fallos previamente diagnosticados y correcciones. Incluye registros al reportar uno nuevo.](Process.md "Process.md")
- **Un servidor (SSH, Apache, etc.) no arranca:** esperado. Necesitan funciones root. Usa `tightvncserver` en lugar de SSH y `wbox` para un pequeño servidor web de intercambio de archivos, como también aconsejaba la app original.
Para informes de errores, incluye el modelo del dispositivo, la versión de Android, los identificadores de compilación/imagen, los pasos de reproducción y los registros relevantes. Elimina primero la información personal. "No funciona" es un sentimiento perfectamente comprensible, pero un informe de error difícil de depurar.

## Gracias, y el papeleo
Gracias a [pelya y colaboradores](https://github.com/pelya), [Debian](https://www.debian.org/), [XFCE](https://xfce.org/), [Termux:X11](https://github.com/termux/termux-x11), [PRoot](https://proot-me.github.io/), [PulseAudio](https://www.freedesktop.org/wiki/Software/PulseAudio/) y los demás proyectos upstream. La app original hizo que esta idea maravillosamente poco práctica mereciera la pena revisitarse.

Los componentes conservan sus propias licencias. Consulta [THIRD_PARTY.md](THIRD_PARTY.md), [licenses.md](licenses.md) y los textos de licencia preservados. La redistribución binaria debe incluir el código fuente/compilación correspondiente y el material de reenlazado que esas licencias requieren. Este README no relicencia el proyecto, incluso si has leído hasta el final. Especialmente entonces.
