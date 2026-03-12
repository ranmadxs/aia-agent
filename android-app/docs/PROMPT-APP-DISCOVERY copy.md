# Prompt para la IA de la app Android (UDP Discovery)

Copia y pega este texto para la IA que trabaja en la app:

---

## Problema

Cuando el usuario conecta el móvil al WiFi del ESP (AP del dispositivo, SSID tipo `1A2B3C4D_YUS-1.0.6-COSTA`), el dispositivo **no aparece en el discovery**. Solo se descubre cuando el móvil está en la red del router (fuera del AP del ESP).

**Causa:** El ESP8266 no recibe bien broadcast UDP cuando actúa como AP. Por eso el microcontrolador envía **broadcast proactivo** cada 2 segundos a `192.168.50.255:9999` y `255.255.255.255:9999` con el JSON de discovery. La app debe **escuchar en el puerto 9999** para recibir esos anuncios.

## Cambio requerido en la app

La app debe tener un socket UDP con **bind al puerto 9999** (ej: `0.0.0.0:9999` o `InetAddress.getByName("0.0.0.0")` en el puerto 9999). Así podrá recibir:

1. **Respuestas unicast** a AIA-DISCOVER (cuando el móvil está en la red del router)
2. **Broadcasts proactivos** del ESP (cuando el móvil está conectado al AP del dispositivo, 192.168.50.x)

Si la app actualmente envía AIA-DISCOVER desde un puerto efímero y espera respuestas solo en ese puerto, **no recibirá los broadcasts** porque estos llegan al puerto 9999.

## Flujo correcto

1. Crear `DatagramSocket` y hacer **bind a puerto 9999** (no usar puerto efímero)
2. Enviar "AIA-DISCOVER" a `255.255.255.255:9999`
3. En loop, `socket.receive()` en ese mismo socket
4. Si llega un paquete con JSON válido que contiene `device_id` (o `id`) e `ip` → tratarlo como dispositivo descubierto y añadirlo a la lista
5. Los mensajes con `{"topic":"yai-mqtt/out",...}` son lecturas del sensor

## Formato JSON de discovery

```json
{
  "id": "EstanqueCosta",
  "device_id": "YUS-1.1.7-COSTA",
  "channel_id": "1A2B3C4D",
  "ip": "192.168.50.1",
  "version": "1.1.7-YUS-COSTA",
  "mqtt_topic_in": "yai-mqtt/in",
  "mqtt_topic_out": "yai-mqtt/out",
  "name": "EstanqueCosta v1.1.7-YUS-COSTA",
  "type": "estanque"
}
```

**Criterio:** Cualquier JSON con `device_id` e `ip` = dispositivo descubierto.

## Importante: evitar duplicados

La app puede recibir su **propio broadcast** (eco de "AIA-DISCOVER"). No debe tratarlo como dispositivo.

- **Ignorar** paquetes que contengan solo "AIA-DISCOVER" (o que no sean JSON válido).
- **Solo procesar** paquetes que sean JSON válido con `device_id` e `ip`.
- Antes de añadir a la lista, comprobar por `device_id` si ya existe para no duplicar.

## Constantes

- Puerto: **9999**
- Mensaje discovery: **AIA-DISCOVER**
- Broadcast destino: **255.255.255.255:9999**

---

## Bind Error al abrir datos del sensor

**Problema:** La app se cae con "Bind Error" al abrir la pantalla de datos del sensor.

**Causa:** Varias partes de la app intentan hacer bind al puerto 9999. Solo puede haber un socket escuchando en ese puerto. Si el discovery ya tiene un socket en 9999 y la pantalla de datos del sensor intenta crear otro, falla con "Address already in use" / Bind Error.

**Solución:** Usar un **único socket UDP compartido** para discovery y datos del sensor:

1. Crear el socket una sola vez (Application, singleton o Service).
2. Reutilizar ese mismo socket para: discovery, recepción de lecturas (`topic: yai-mqtt/out`) y envío de comandos.
3. No crear sockets nuevos en cada Activity/Fragment. Si la pantalla de datos del sensor necesita recibir lecturas, debe usar el socket existente, no crear uno nuevo.
4. El socket puede vivir en un Service en segundo plano que notifica a las pantallas cuando llegan datos.

### Canal UDP y estructura de datos del sensor

**Puerto:** 9999 (mismo que discovery)

**Canal de lecturas:** Los mensajes con `topic: "yai-mqtt/out"` son lecturas del sensor. El ESP envía al subscriber (IP:puerto de la app) en el mismo socket UDP.

**Formato del mensaje UDP:**
```json
{
  "topic": "yai-mqtt/out",
  "payload": {
    "deviceId": "YUS-1.1.7-COSTA",
    "channelId": "1A2B3C4D",
    "status": "OKO",
    "distanceCm": 25.5,
    "timestamp": "2024-01-15 14:30:25",
    "tankDepthCm": 160.0,
    "remainingToFullCm": 134.5,
    "fillLevelPercent": 84.06,
    "litros": 4203,
    "levelBar": "[########--]"
  }
}
```

**Campos del payload:**

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `deviceId` | string | ID del dispositivo |
| `channelId` | string | ID corto del canal (8 hex) |
| `status` | string | "OKO" (OK) o "NOK" (error) |
| `distanceCm` | number | Distancia al agua en cm |
| `timestamp` | string | Fecha/hora de la lectura |
| `tankDepthCm` | number | Profundidad total del tanque en cm |
| `remainingToFullCm` | number | cm restantes hasta lleno |
| `fillLevelPercent` | number | Porcentaje de llenado (0-100) |
| `litros` | number | Litros estimados en el tanque |
| `levelBar` | string | Barra visual "[########--]" |

---
