<p align="center">
  <img src="design/icono-original.png" width="128" alt="Icono de PillMate">
</p>

<h1 align="center">PillMate</h1>

<p align="center">
  Registra la medicación que tomas <b>cuando la tomas</b> y descubre tu propio ritmo.<br>
  Sin alarmas que te persigan. Sin cuentas. Sin internet.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0 o superior">
  <img src="https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.2">
  <img src="https://img.shields.io/badge/internet-sin%20permiso-0F9D8A" alt="Sin permiso de internet">
  <img src="https://img.shields.io/badge/licencia-GPL--3.0--or--later-blue" alt="Licencia GPL-3.0-or-later">
</p>

<p align="center">
  <img src="screenshots/01-home.png" width="23%" alt="Pantalla principal: accesos rápidos y reloj del día">
  <img src="screenshots/02-historial-dia.png" width="23%" alt="Historial de un día con una toma seleccionada en el reloj">
  <img src="screenshots/03-historial-semana.png" width="23%" alt="Historial de una semana: barras por hora">
  <img src="screenshots/04-estadisticas.png" width="23%" alt="Estadísticas: ritmo de 24 horas y datos calculados">
</p>

<p align="center"><sub>Capturas con datos de ejemplo.</sub></p>

---

**Contenido:** [Qué es](#qué-es) · [Qué hace](#qué-hace) · [Privacidad](#privacidad) · [Lo que no es](#lo-que-no-es) · [Instalar](#instalar) · [Compilar](#compilar) · [Cómo está hecha](#cómo-está-hecha) · [Contribuir](#contribuir) · [Licencia](#licencia)

## Qué es

PillMate es una app Android para medicación **a demanda**: la que tomas cuando la necesitas, no a una hora fija. Apuntas cada toma con un toque y la app te devuelve tu patrón: a qué horas, cuánto y con qué separación.

No es un pastillero de alarmas. Los recordatorios existen, pero son opcionales.

Se apoya en tres ideas, y ninguna es negociable:

1. **Funciona sin internet.** No es que no lo use: no puede usarlo.
2. **Registrar cuesta un toque.** Si apuntar es más lento que no apuntar, nadie apunta.
3. **Describe, no juzga.** Cuenta lo que hay. No puntúa, no fija objetivos, no aconseja.

> **Estado:** `2.0.0-beta.1`. Funciona y se usa a diario, pero solo se ha probado en unos pocos dispositivos: si algo falla, abre un *issue*. Aún no está en Google Play ni hay APK publicado.

## Qué hace

### Registrar

- **Un toque.** Cuatro accesos rápidos en la pantalla principal. Pulsación larga para ajustar cantidad u hora. Si te equivocas, *Deshacer*.
- **Sin abrir la app.** Un widget de escritorio y una notificación persistente con tus medicamentos habituales.
- **Widget a tu gusto.** Se redimensiona (2×2, una fila o una columna) y se adapta solo. Tres formas, tres densidades y ocho colores.
- **Notas.** De texto en cada toma, y de voz cifradas.

### Entender

- **Reloj de 24 horas.** Cada toma es un punto con el color de su medicamento. Con un día ves el momento exacto; con una semana o un mes, los puntos se convierten en barras y ves a qué horas se concentran. Toca un punto o una barra para ver qué fue.
- **Tu patrón, en números.** Hora más activa, intervalo habitual entre tomas, medicamento más registrado y día con más tomas. Calculado con aritmética, sin IA.
- **Estadísticas por medicamento.** Cantidad por día (mg, ml, gotas) y número de tomas, con las distintas dosis de un mismo medicamento agrupadas.
- **Historial.** Por día, semana o mes, con búsqueda en las notas.

### Tus datos

- **Copia de seguridad** completa en ZIP, restaurable. Exportación a **CSV** y a **PDF**.
- **Bloqueo biométrico** opcional.

### Y además

- **Introducción interactiva** al abrir la app por primera vez: cuatro páginas que se tocan, no que se leen. Se puede repetir desde Ajustes.
- **Dos diseños:** *Ritmo* (el nuevo) y *Clásico*, con tema claro, oscuro y Material You.

## Privacidad

La app **no tiene permiso de internet**. No puede enviar nada a ningún sitio, y hay un test que falla si alguien se lo añade ([`PrivacyContractTest`](app/src/test/java/com/d4vram/pillmate2/ai/PrivacyContractTest.kt)).

- Sin cuentas, sin analítica, sin publicidad, sin servicios de terceros.
- Todo se guarda en el dispositivo. Las copias salen solo cuando tú las exportas.

Permisos que sí pide, y para qué:

| Permiso | Para qué |
|---|---|
| Notificaciones, alarmas exactas, arranque | Recordatorios opcionales y notificación de toma rápida |
| Micrófono | Notas de voz |
| Cámara y fotos | Foto del medicamento |
| Biometría | Bloqueo de la app |

## Lo que no es

PillMate describe lo que registras; **no da consejo médico**. No calcula dosis máximas, no avisa de interacciones y no puntúa tu "adherencia". No es un producto sanitario. Las decisiones sobre tu medicación son tuyas y de quien te la prescribe.

## Instalar

Todavía no hay versión publicada. Cuando la haya, el APK estará en [Releases](https://github.com/D4vRAM369/PillMate/releases). Mientras tanto, se compila desde el código.

## Compilar

Necesitas Android Studio (o el SDK de Android) y JDK 17 o superior.

```bash
git clone https://github.com/D4vRAM369/PillMate.git
cd PillMate
./gradlew :app:assembleDebug        # compila
./gradlew :app:testDebugUnitTest    # tests unitarios
./gradlew :app:installDebug         # instala en un dispositivo conectado
```

Si ya usas PillMate en ese dispositivo y solo quieres probar cambios, añade `-Pdev`: la build se instala como **PillMate Dev** (`dev.d4vram.pillmate2`), una app aparte con su propia base de datos, y tus datos reales no se tocan.

```bash
./gradlew :app:installDebug -Pdev
```

Para compilar `release` hace falta un `keystore.properties` en la raíz (no se versiona) con `storeFile`, `storePassword`, `keyAlias` y `keyPassword`.

## Cómo está hecha

Kotlin · Jetpack Compose y Material 3 · Room · DataStore · Glance (widget) · Navigation Compose. Mínimo Android 8.0 (API 26), compilada contra Android 15 (API 35).

Un solo módulo, arquitectura MVVM: pantallas en Compose, `ViewModel` con `StateFlow`, un repositorio y Room debajo.

```
app/src/main/java/com/d4vram/pillmate2/
├── data/            Room (entidades, DAO, migraciones), repositorio, copias y exportación
├── model/           Modelos que usa la interfaz
├── ai/              SmartInsightEngine: el "patrón en números". Aritmética, pese al nombre
├── notifications/   Recordatorios y notificación de toma rápida
├── widget/          Widget de escritorio (Glance)
└── ui/
    ├── screens/     Home, Medicamentos, Historial, Estadísticas, Notas, Ajustes
    ├── timeline/    Reloj de 24 horas
    ├── onboarding/  Introducción
    └── theme/       Temas, diseños y preferencias
```

El esquema de la base de datos se exporta a [`app/schemas/`](app/schemas) para poder comprobar las migraciones.

## Contribuir

Los *issues* con fallos o ideas son bienvenidos. Antes de proponer una función, ten en cuenta los tres principios de arriba y uno más: la app no usa IA.

Si envías cambios: que `./gradlew :app:testDebugUnitTest` pase, y nada de datos de medicación reales en ejemplos, capturas ni tests.

## Licencia

Copyright © 2025-2026 D4vRAM369

PillMate es software libre: puedes redistribuirlo y modificarlo bajo los términos de la [GNU General Public License, versión 3](LICENSE) o, si lo prefieres, cualquier versión posterior. Se distribuye con la esperanza de que sea útil, pero **sin ninguna garantía**.

En corto: puedes usarla, estudiarla, cambiarla y compartirla. Si distribuyes una versión modificada, tiene que seguir siendo libre y bajo esta misma licencia.
