# Especificación: UDP Discovery para Microcontrolador

Documento para que una IA implemente el lado del **microcontrolador** (ESP32, NodeMCU, Raspberry Pi, etc.) del protocolo de discovery usado por la app Android AIA Agent.

---

## Resumen

La app Android envía un **broadcast UDP** en la red local. Cualquier microcontrolador en la misma red debe **escuchar** ese mensaje y **responder** con su identificación e IP. Así la app descubre dispositivos sin configuración manual.

---

## Protocolo

### Puerto

| Campo | Valor |
|-------|-------|
| **Puerto UDP** | `9999` |

### Mensaje de discovery (App → Red)

La app envía por broadcast a `255.255.255.255:9999`:

```
AIA-DISCOVER
```

- Codificación: UTF-8
- Sin salto de línea al final
- El microcontrolador debe reconocer exactamente esta cadena (o un prefijo como `AIA-DISCOVER`)

### Respuesta (Microcontrolador → App)

El microcontrolador responde al **IP y puerto de origen** del mensaje recibido. La app espera uno de estos formatos:

#### Formato 1: `ID:IP` (simple)

```
YUS-0.2.8-COSTA:192.168.4.1
```

- `ID`: Identificador único del dispositivo (ej: YUS-0.2.8-COSTA, nodemcu-01)
- `IP`: IP del microcontrolador en la red
- Separador: `:` (dos puntos)

#### Formato 2: JSON

```json
{"id":"YUS-0.2.8-COSTA","ip":"192.168.4.1","name":"Estanque Costa","type":"estanque","device_id":"YUS-0.2.8-COSTA","channel_id":"1A2B3C4D","version":"1.0.6","mqtt_topic_in":"yai-mqtt/in","mqtt_topic_out":"yai-mqtt/out"}
```

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | string | Identificador único (obligatorio) |
| `ip` | string | IP del dispositivo (obligatorio) |
| `device_id` | string | ID del dispositivo con versión |
| `channel_id` | string | ID corto del canal (8 hex) |
| `version` | string | Versión del firmware |
| `mqtt_topic_in` | string | Canal para recibir comandos (yai-mqtt/in) |
| `mqtt_topic_out` | string | Canal donde se envían lecturas (yai-mqtt/out) |
| `name` | string | Nombre legible (opcional) |
| `type` | string | Tipo: estanque, sensor, etc. (opcional) |

### Canales UDP (mismo puerto 9999)

Tras el discovery, la app queda como *subscriber*. El microcontrolador envía y recibe mensajes con estructura `{"topic":"...","payload":...}`:

| Topic | Dirección | Descripción |
|-------|-----------|-------------|
| `yai-mqtt/out` | ESP → App | Lecturas del sensor (JSON con deviceId, distanceCm, fillLevelPercent, etc.) |
| `yai-mqtt/in` | App → ESP | Comandos (ej: `{"topic":"yai-mqtt/in","payload":"ON,2000,0,0,0,0,0,0"}`) |

**Lecturas (yai-mqtt/out):** El ESP envía `{"topic":"yai-mqtt/out","payload":{...lectura_json...}}` al subscriber.

**Comandos (yai-mqtt/in):** La app envía `{"topic":"yai-mqtt/in","payload":"ON,2000,0,0,0,0,0,0"}` o `"OFF,0,0,0,0,0,0,0"`. El ESP ejecuta el comando igual que por MQTT/Serial.

---

## Flujo

### 1. Discovery

```
1. App envía UDP "AIA-DISCOVER" a 255.255.255.255:9999
2. Microcontrolador recibe el mensaje
3. Microcontrolador obtiene (IP_app, Puerto_app) del paquete
4. Microcontrolador guarda (IP_app, Puerto_app) como "subscriber"
5. Microcontrolador envía respuesta JSON al (IP_app, Puerto_app)
6. App recibe la respuesta y añade el dispositivo a la lista
```

### 2. Lecturas (ESP → App)

Tras el discovery, el microcontrolador envía cada lectura al subscriber:

```
{"topic":"yai-mqtt/out","payload":{"deviceId":"YUS-1.0.6","channelId":"1A2B3C4D","status":"OKO","distanceCm":25.5,"timestamp":"2024-01-15 14:30:25","tankDepthCm":160.0,"remainingToFullCm":134.5,"fillLevelPercent":84.06,"litros":4203,"levelBar":"[########--]"}}
```

### 3. Comandos (App → ESP)

La app envía comandos al microcontrolador (mismo puerto 9999, IP del ESP):

```
{"topic":"yai-mqtt/in","payload":"ON,2000,0,0,0,0,0,0"}
{"topic":"yai-mqtt/in","payload":"OFF,0,0,0,0,0,0,0"}
```

El ESP ejecuta el comando igual que por MQTT o Serial.

### 4. Timeout del subscriber

Si no hay discovery en 5 minutos, el microcontrolador deja de enviar lecturas al subscriber. La app puede reenviar `AIA-DISCOVER` para reactivar.

### 5. Intervalos de envío (NodeMCU → Red)

| Tipo | Intervalo | Canal |
|------|-----------|-------|
| Broadcast discovery (metadatos del dispositivo) | 2 segundos | Broadcast a 192.168.50.255 y 255.255.255.255 |
| Lecturas del sensor (nivel, distancia, litros, etc.) | 1,5 segundos | Unicast al subscriber (app que envió AIA-DISCOVER) |

Las lecturas del sensor se envían cada 1,5 segundos cuando hay subscriber. El intervalo se puede cambiar con el comando Serial `ON,2000` (por ejemplo, para 2 segundos).

### 6. Lado app: recepción de datos

La app **no tiene intervalo de lectura**. Usa `socket.receive()` en bloque: espera hasta que llegue un paquete y lo procesa de inmediato. La recepción es **event-driven** (cada paquete que envía el NodeMCU se recibe y procesa al instante). En la práctica, la app recibe datos cada ~1,5 segundos, coincidiendo con el envío del NodeMCU.

---

## Implementación por plataforma

### ESP32 / ESP8266 / NodeMCU (Arduino)

```cpp
#include <WiFi.h>
#include <WiFiUdp.h>

WiFiUDP udp;
const int DISCOVERY_PORT = 9999;
const char* DISCOVER_MSG = "AIA-DISCOVER";
const char* DEVICE_ID = "YUS-0.2.8-COSTA";  // Tu identificador

void setup() {
  WiFi.mode(WIFI_AP_STA);  // o WIFI_STA si conectas a router
  WiFi.softAP("MiRed", "password");  // o WiFi.begin() si STA
  udp.begin(DISCOVERY_PORT);
}

void loop() {
  int packetSize = udp.parsePacket();
  if (packetSize > 0) {
    char buf[64];
    int len = udp.read(buf, sizeof(buf) - 1);
    buf[len] = '\0';
    
    if (strstr(buf, DISCOVER_MSG) != NULL) {
      IPAddress remoteIP = udp.remoteIP();
      int remotePort = udp.remotePort();
      
      // Formato ID:IP
      String response = String(DEVICE_ID) + ":" + WiFi.softAPIP().toString();
      // Si usas STA: WiFi.localIP().toString()
      
      udp.beginPacket(remoteIP, remotePort);
      udp.print(response);
      udp.endPacket();
    }
  }
  delay(10);
}
```

### Raspberry Pi (Python)

```python
import socket

DISCOVERY_PORT = 9999
DISCOVER_MSG = b"AIA-DISCOVER"
DEVICE_ID = "YUS-0.2.8-COSTA"

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
sock.bind(('', DISCOVERY_PORT))

while True:
    data, addr = sock.recvfrom(256)
    if DISCOVER_MSG in data:
        import netifaces
        ip = netifaces.ifaddresses('wlan0')[2][0]['addr']  # o eth0
        response = f"{DEVICE_ID}:{ip}"
        sock.sendto(response.encode(), addr)
```

### Node.js (Raspberry Pi, etc.)

```javascript
const dgram = require('dgram');
const DISCOVERY_PORT = 9999;
const DISCOVER_MSG = 'AIA-DISCOVER';
const DEVICE_ID = 'YUS-0.2.8-COSTA';

const sock = dgram.createSocket('udp4');
sock.bind(DISCOVERY_PORT);

sock.on('message', (msg, rinfo) => {
  if (msg.toString().includes(DISCOVER_MSG)) {
    const os = require('os');
    const ip = Object.values(os.networkInterfaces())
      .flat().find(i => !i.internal && i.family === 'IPv4')?.address || '0.0.0.0';
    const response = `${DEVICE_ID}:${ip}`;
    sock.send(response, rinfo.port, rinfo.address);
  }
});
```

---

## Requisitos

1. **Misma red**: El móvil y el microcontrolador deben estar en la misma red WiFi (o el móvil conectado al AP del dispositivo).
2. **Puerto 9999**: El microcontrolador debe escuchar en UDP 9999.
3. **Mensaje**: Reconocer `AIA-DISCOVER` (o contenerlo en el payload).
4. **Respuesta**: Enviar al `remoteIP` y `remotePort` del paquete recibido.
5. **Formato**: `ID:IP` o JSON con `id` e `ip`.

---

## Resumen para IA

### Microcontrolador (ESP)

| Paso | Acción |
|-----|--------|
| 1 | Crear socket UDP y hacer bind al puerto 9999 |
| 2 | En loop, leer paquetes entrantes |
| 3 | Si contiene "AIA-DISCOVER": guardar (remoteIP, remotePort) como subscriber, enviar JSON de discovery |
| 4 | Si es JSON con `topic":"yai-mqtt/in"`: extraer `payload` y ejecutar comando |
| 5 | Cuando hay lectura: enviar `{"topic":"yai-mqtt/out","payload":{...}}` al subscriber |
| 6 | Subscriber expira a los 5 min sin discovery |

### App (cliente)

| Paso | Acción |
|-----|--------|
| 1 | Enviar "AIA-DISCOVER" por broadcast a 255.255.255.255:9999 |
| 2 | Escuchar respuestas en el mismo puerto (9999 o el que se use) |
| 3 | Parsear JSON de discovery (device_id, ip, mqtt_topic_in, mqtt_topic_out, etc.) |
| 4 | Para recibir lecturas: seguir escuchando en el puerto; los mensajes con `topic":"yai-mqtt/out"` son lecturas |
| 5 | Para enviar comandos: enviar `{"topic":"yai-mqtt/in","payload":"ON,2000,..."}` a (IP_ESP, 9999) |

**Constantes:**
- Puerto: 9999
- Mensaje discovery: AIA-DISCOVER
- Topics: yai-mqtt/out (lecturas), yai-mqtt/in (comandos)
