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

### Crítico: mismo socket para enviar y recibir

El ESP guarda como subscriber la **IP y puerto de origen** del paquete AIA-DISCOVER que recibe. Las lecturas se envían a esa misma dirección.

La app debe enviar AIA-DISCOVER desde el **mismo socket** que está en el puerto 9999. Si usa otro socket (puerto efímero), el ESP enviará las lecturas a ese puerto y la app no las recibirá.

## Flujo correcto

1. Crear `DatagramSocket` y hacer **bind a puerto 9999** (no usar puerto efímero)
2. Enviar "AIA-DISCOVER" a `255.255.255.255:9999` (broadcast) y a `device.ip:9999` (unicast al seleccionar dispositivo)
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

## Constantes

- Puerto: **9999**
- Mensaje discovery: **AIA-DISCOVER**
- Broadcast destino: **255.255.255.255:9999**

## Cómo depurar

1. Abre el monitor serial del ESP (115200 baud).
2. Conecta el móvil al AP del ESP y abre la pantalla del estanque.
3. **Si ves** `UDP Discovery >> subscriber registered: 192.168.50.x:9999` → el ESP recibe el unicast; si no llegan lecturas, el problema está en la app (socket o puerto).
4. **Si no ves** `subscriber registered` → el ESP no recibe el unicast; revisa que la app envíe desde el socket en el puerto 9999.

---
