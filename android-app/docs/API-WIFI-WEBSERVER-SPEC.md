# API WiFi WebServer - Sensor Ultrasónico ESP

**Para la IA que desarrolla la app Android.** Este documento describe la API HTTP del servidor embebido en el ESP8266/ESP32 para configuración WiFi.

## Contexto

Cuando el usuario conecta el móvil al WiFi del sensor (AP del ESP, SSID tipo `1A2B3C4D_YUS-1.3.0-COSTA`), el ESP actúa como punto de acceso con IP `192.168.50.1`. La app puede llamar a estos endpoints para listar redes WiFi y conectar el sensor a una nueva red.

**URL base:** `http://192.168.50.1`  
**Puerto:** 80  
**Content-Type:** `application/json`

---

## GET /api/wifi

Lista las redes WiFi que el ESP puede ver y la red a la que está conectado actualmente.

### Request

```
GET http://192.168.50.1/api/wifi
```

Sin body. Sin headers requeridos.

### Response 200 OK

```json
{
  "connected_ssid": "MiRedActual",
  "connected_rssi": -45,
  "networks": [
    {
      "ssid": "MiRedActual",
      "rssi": -45,
      "encryption": "encrypted"
    },
    {
      "ssid": "OtraRed",
      "rssi": -72,
      "encryption": "open"
    }
  ]
}
```

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `connected_ssid` | string | SSID de la red conectada. Vacío `""` si no hay conexión |
| `connected_rssi` | int | Señal RSSI (dBm) de la red conectada. `0` si no conectado |
| `networks` | array | Lista de redes visibles en el scan |
| `networks[].ssid` | string | Nombre de la red |
| `networks[].rssi` | int | Señal RSSI (dBm), típicamente -30 a -90 |
| `networks[].encryption` | string | `"open"` o `"encrypted"` |

**Nota:** El scan puede tardar 2-5 segundos. La app debe mostrar loading.

---

## POST /api/wifi

Intenta conectar el ESP a una red WiFi. Recibe SSID y contraseña. Si la conexión es exitosa, el ESP imprime por Serial y responde 200. No guarda credenciales en flash (se pierden al reiniciar).

### Request

```
POST http://192.168.50.1/api/wifi
Content-Type: application/json

{
  "ssid": "NombreDeLaRed",
  "password": "clave123"
}
```

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `ssid` | string | Sí | Nombre de la red WiFi |
| `password` | string | No | Contraseña. Vacío `""` para redes abiertas |

### Response 200 OK (éxito)

```json
{
  "ok": true,
  "message": "Conectado a NombreDeLaRed"
}
```

### Response 400 (error)

**Body JSON requerido:**
```json
{"error": "Body JSON required"}
```

**JSON inválido:**
```json
{"error": "Invalid JSON"}
```

**SSID faltante:**
```json
{"error": "ssid required"}
```

**Conexión fallida (timeout 15s):**
```json
{"error": "No se pudo conectar a NombreDeLaRed"}
```

---

## Otros endpoints

Cualquier ruta no definida devuelve:

**404 Not Found**
```json
{"error": "Not found"}
```

---

## Flujo típico en la app

1. Usuario conecta móvil al WiFi del sensor (AP del ESP)
2. App abre `http://192.168.50.1/api/wifi` (GET) → muestra lista de redes y conectada actual
3. Usuario selecciona red y escribe contraseña
4. App envía POST con `{"ssid":"...","password":"..."}`
5. Si 200: mostrar mensaje de éxito. El sensor queda conectado a la nueva red (hasta reinicio)
6. Si 400: mostrar mensaje de error al usuario
