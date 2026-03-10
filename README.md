# AIA Agent - MQTT Worker

Worker que escucha la cola MQTT del sensor de estanque y guarda promedios en MongoDB.

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

## Ejecución local

```bash
# Crear .env con las variables
pip install -r requirements.txt
python mqtt_worker.py
```

## Ejecución con Gunicorn (producción)

```bash
pip install gunicorn
gunicorn --worker-class=sync --workers=1 --bind=0.0.0.0:8000 mqtt_worker:main
```

## Deploy en Render

1. Crear "Background Worker"
2. Conectar el repo
3. Build: `pip install -r requirements.txt`
4. Start: `python mqtt_worker.py`
5. Configurar variables de entorno

## Funcionamiento

- Escucha mensajes MQTT del sensor
- Acumula 10 lecturas en buffer
- Calcula promedio y guarda en MongoDB
- Clave única: `hora_local` (formato: YYYY-MM-DD HH:MM)
- Si ya existe registro para ese minuto, lo actualiza
