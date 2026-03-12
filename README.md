# AIA Agent
Users/ranmadxs/trabajos/aia-agent/android-app/app/build/outputs/apk/debug/
Agentes inteligentes para automatización.
{"connected_ssid":"Patitas","connected_rssi":-79,"networks":[{"ssid":"Patitas","rssi":-74,"encryption":"encrypted"}]}
## App Android

En `android-app/` hay una app Android (Kotlin + Jetpack Compose) que:
- Muestra el nivel del estanque en tiempo real (polling cada 3s)
- Permite ejecutar **Forzar Guardado** contra el Tomi Metric Collector

Abrir `android-app/` en Android Studio y ejecutar. Ver [android-app/README.md](android-app/README.md).

## Comandos

```bash
# Instalar dependencias
poetry install

# MQTT Worker - Escucha sensor de estanque y guarda en MongoDB
poetry run mqtt-worker

# WhatsApp Reader - Lee mensajes de un chat (http://localhost:5001)
poetry run whatsapp-reader

# WhatsApp Sender - Envía mensajes a números (http://localhost:5002)
poetry run whatsapp-sender
```

## Workers disponibles

| Comando | Descripción | Puerto |
|---------|-------------|--------|
| `poetry run mqtt-worker` | Escucha MQTT del sensor de estanque | - |
| `poetry run whatsapp-reader` | Lee mensajes de WhatsApp | 5001 |
| `poetry run whatsapp-sender` | Envía mensajes por WhatsApp | 5002 |

---

## MQTT Worker

Escucha la cola MQTT del sensor de estanque y guarda promedios en MongoDB.

## Configuración

Variables de entorno requeridas:

| Variable | Descripción |
|----------|-------------|
| `MONGODB_URI` | URI de conexión a MongoDB |
| `MQTT_HOST` | Host del broker MQTT |
| `MQTT_PORT` | Puerto MQTT (default: 1883) |
| `MQTT_USERNAME` | Usuario MQTT |
| `MQTT_PASSWORD` | Contraseña MQTT |
| `MQTT_TOPIC_OUT` | Topic a escuchar |

## Instalación

```bash
poetry install
```

## Ejecución local

```bash
# Crear .env con las variables
poetry run mqtt-worker
```

## Deploy en Render

1. Crear "Background Worker"
2. Conectar el repo
3. Build: `pip install poetry && poetry install`
4. Start: `poetry run mqtt-worker`
5. Configurar variables de entorno

## Funcionamiento

- Escucha mensajes MQTT del sensor
- Acumula 10 lecturas en buffer
- Calcula promedio y guarda en MongoDB
- Clave única: `hora_local` (formato: YYYY-MM-DD HH:MM)
- Si ya existe registro para ese minuto, lo actualiza
