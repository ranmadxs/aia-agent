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
{"id":"YUS-0.2.8-COSTA","ip":"192.168.4.1","name":"Estanque Costa","type":"estanque"}
```

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | string | Identificador único (obligatorio) |
| `ip` | string | IP del dispositivo (obligatorio) |
| `name` | string | Nombre legible (opcional) |
| `type` | string | Tipo: estanque, sensor, etc. (opcional) |

---

## Flujo

```
1. App Android envía UDP broadcast "AIA-DISCOVER" a 255.255.255.255:9999
2. Microcontrolador recibe el mensaje (está en la misma red)
3. Microcontrolador verifica que el mensaje sea "AIA-DISCOVER"
4. Microcontrolador obtiene IP_origen y Puerto_origen del paquete
5. Microcontrolador envía respuesta a (IP_origen, Puerto_origen)
6. App recibe la respuesta y añade el dispositivo a la lista
```

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

| Paso | Acción |
|-----|--------|
| 1 | Crear socket UDP y hacer bind al puerto 9999 |
| 2 | En loop, leer paquetes entrantes |
| 3 | Si el payload contiene "AIA-DISCOVER", extraer IP y puerto del remitente |
| 4 | Obtener la IP propia del dispositivo |
| 5 | Enviar respuesta "ID:IP" o JSON al (IP_remitente, Puerto_remitente) |
| 6 | Repetir |

**Constantes:**
- Puerto: 9999
- Mensaje a reconocer: AIA-DISCOVER
