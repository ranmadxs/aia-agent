# AIA Agent - Android App

App Android para monitorear el estanque y ejecutar forzar-guardado contra el Tomi Metric Collector.

## Flujo

1. **Pantalla inicial**: Sonar 360° + escaneo UDP en la red WiFi
2. **Selección**: El usuario elige un dispositivo de la lista
3. **Monitor**: Se muestra el nivel del estanque y botón Forzar Guardado

## Funcionalidades

- **UDP Discovery**: Broadcast "AIA-DISCOVER" en puerto 9999, descubre microcontroladores (ESP32, NodeMCU, etc.) en la misma red
- **Estado en tiempo real**: Polling cada 3s a `GET /monitor/api/estado`
- **Tanque visual**: Barra de nivel según porcentaje
- **Métricas**: Estado, distancia, altura agua, última lectura
- **Forzar guardado**: Botón para `POST /monitor/api/historial/forzar-guardado` con `X-Aia-Origin: YUS_028costa`

## Requisitos

- Android Studio Hedgehog (2023.1.1) o superior
- minSdk 26, targetSdk 34
- Kotlin 1.9+
- Jetpack Compose

## Configuración

La URL base está en `RetrofitClient.kt`:

```kotlin
private const val DEFAULT_BASE_URL = "https://tomi-metric-collector-production.up.railway.app/"
```

Para cambiar `X-Aia-Origin`, `channelId` o `deviceId`, edita `EstanqueViewModel.forzarGuardado()`.

## Cómo ejecutar

1. Abrir el proyecto en Android Studio
2. Conectar un dispositivo o emulador
3. Run ▶️

```bash
# O desde terminal (con Gradle wrapper)
./gradlew installDebug
```

## Spec para microcontrolador

Ver [docs/UDP-DISCOVERY-MICROCONTROLLER-SPEC.md](docs/UDP-DISCOVERY-MICROCONTROLLER-SPEC.md) para implementar el discovery en ESP32, NodeMCU, Raspberry Pi, etc.

## Estructura

```
app/src/main/java/cl/tomi/aiaagent/
├── MainActivity.kt          # UI principal (Compose)
├── api/
│   ├── EstanqueApi.kt       # Interface Retrofit
│   └── RetrofitClient.kt    # Cliente HTTP
├── data/
│   ├── EstanqueEstado.kt
│   ├── ForzarGuardadoRequest.kt
│   └── ForzarGuardadoResponse.kt
└── viewmodel/
    └── EstanqueViewModel.kt # Lógica y polling
```

## Dependencias

- Retrofit + Gson para API REST
- Jetpack Compose + Material3
- Coroutines + Flow
