# Especificación: Configuración WiFi vía UDP (NodeMCU/ESP)

Documento para que una IA implemente en el **microcontrolador** (NodeMCU, ESP8266, ESP32) la acción de recibir credenciales WiFi desde la app Android y conectarse a una nueva red.

---

## Resumen

La app envía SSID y contraseña de una red WiFi al ESP por UDP. El NodeMCU intenta conectarse. Si la conexión es exitosa, guarda las credenciales en memoria persistente. Si falla, responde con un mensaje de error.

---

## Canal

| Campo | Valor |
|-------|-------|
| **Topic** | `yai-mqtt/in` (mismo canal que comandos ON/OFF) |
| **Dirección** | App → ESP (UDP al IP del dispositivo, puerto 9999) |

El topic para **enviar** comandos al ESP usa `in` al final (no `out`). El `out` es para lecturas ESP → App.

---

## Mensaje (App → ESP)

La app envía un JSON con estructura `{"topic":"yai-mqtt/in","payload":{...}}`:

```json
{
  "topic": "yai-mqtt/in",
  "payload": {
    "cmd": "WIFI_CONFIG",
    "ssid": "MiRedWiFi",
    "password": "miClaveSecreta"
  }
}
```

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `cmd` | string | Siempre `"WIFI_CONFIG"` para esta acción |
| `ssid` | string | Nombre de la red WiFi |
| `password` | string | Contraseña de la red |

---

## Comportamiento del NodeMCU

### 1. Recibir el mensaje

Cuando el ESP recibe un paquete UDP con `topic:"yai-mqtt/in"` y `payload.cmd == "WIFI_CONFIG"`:

1. Extraer `ssid` y `password` del payload
2. Intentar conectar a la red con `WiFi.begin(ssid, password)`
3. Esperar un timeout razonable (ej. 10–15 segundos)

### 2. Si la conexión es exitosa

- Guardar SSID y contraseña en SPIFFS/LittleFS o EEPROM (persistente)
- Enviar respuesta de éxito al subscriber (IP y puerto de origen del mensaje):

```json
{
  "topic": "yai-mqtt/out",
  "payload": {
    "cmd": "WIFI_CONFIG",
    "status": "OK",
    "message": "WiFi guardado"
  }
}
```

### 3. Si la conexión falla

- No guardar nada
- Enviar respuesta de error al subscriber:

```json
{
  "topic": "yai-mqtt/out",
  "payload": {
    "cmd": "WIFI_CONFIG",
    "status": "ERROR",
    "message": "No se pudo conectar a la red"
  }
}
```

El campo `message` puede incluir detalles (ej. contraseña incorrecta, red no encontrada, timeout).

---

## Flujo resumido

```
1. App envía {"topic":"yai-mqtt/in","payload":{"cmd":"WIFI_CONFIG","ssid":"...","password":"..."}} al IP del ESP:9999
2. ESP recibe el mensaje
3. ESP intenta WiFi.begin(ssid, password)
4. Si OK → guardar en memoria, enviar {"topic":"yai-mqtt/out","payload":{"cmd":"WIFI_CONFIG","status":"OK",...}}
5. Si falla → enviar {"topic":"yai-mqtt/out","payload":{"cmd":"WIFI_CONFIG","status":"ERROR","message":"..."}}
6. App recibe la respuesta y muestra éxito o error al usuario
```

---

## Notas

- La app debe enviar al **IP del dispositivo** (obtenido en el discovery), no por broadcast
- El ESP debe responder al **IP y puerto de origen** del paquete recibido (el subscriber)
- Tras guardar credenciales, el ESP puede reiniciar para aplicar la nueva red o continuar en modo AP+STA según el diseño del firmware
