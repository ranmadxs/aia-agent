# Especificación: Red AP y topología del sensor ultrasónico

**Para la IA que necesita entender la topología de red.** Describe la red WiFi que levanta el ESP8266/ESP32, las IPs, y la API HTTP disponible.

---

## 1. Red WiFi (Access Point)

El sensor actúa como **punto de acceso WiFi** para que clientes (móvil, PC) se conecten y accedan a la API.

| Campo | Valor |
|-------|-------|
| **SSID** | `{CHANNEL_ID}_{DEVICE_ID}` |
| **Ejemplo** | `1A2B3C4D_YUS-1.3.0-COSTA` |
| **Contraseña** | Sin contraseña (red abierta) |
| **Modo** | `WIFI_AP_STA` (AP + Station; puede estar conectado a un router a la vez) |

---

## 2. IP fija del NodeMCU (gateway)

El ESP tiene una **IP fija** configurada como gateway del AP:

| Campo | Valor |
|-------|-------|
| **IP del ESP** | `192.168.50.1` |
| **Gateway** | `192.168.50.1` |
| **Máscara de subred** | `255.255.255.0` |
| **Subred** | `192.168.50.0/24` |

Configuración en código:
```cpp
IPAddress apLocalIp(192, 168, 50, 1);
IPAddress apSubnetMask(255, 255, 255, 0);
WiFi.softAPConfig(apLocalIp, apLocalIp, apSubnetMask);
```

---

## 3. IPs de los clientes

Los dispositivos que se conectan al AP del ESP reciben IP por **DHCP** del propio ESP:

| Campo | Valor |
|-------|-------|
| **Rango típico** | `192.168.50.2` – `192.168.50.254` |
| **Asignación** | Automática vía DHCP del ESP |
| **Gateway** | `192.168.50.1` |
| **DNS** | Captive portal: todas las peticiones DNS se redirigen a `192.168.50.1` (puerto 53) |

**Nota:** El ESP usa un DNS Server que responde con su IP para capturar tráfico (captive portal). Para acceder a la API HTTP, el cliente debe usar directamente `http://192.168.50.1`.

---

## 4. Topología de red

```
                    ┌─────────────────────┐
                    │   ESP8266/ESP32     │
                    │   (NodeMCU)         │
                    │                     │
                    │  IP: 192.168.50.1   │
                    │  Gateway + DNS      │
                    │  WebServer :80      │
                    │  UDP :9999         │
                    └──────────┬──────────┘
                               │
                    WiFi AP (SSID: 1A2B3C4D_YUS-1.3.0-COSTA)
                               │
         ┌─────────────────────┼─────────────────────┐
         │                     │                     │
         ▼                     ▼                     ▼
   ┌───────────┐         ┌───────────┐         ┌───────────┐
   │  Móvil    │         │  Móvil    │         │  PC       │
   │  .50.2    │         │  .50.3    │         │  .50.4    │
   └───────────┘         └───────────┘         └───────────┘
   (DHCP)                (DHCP)                (DHCP)
```

---

## 5. API HTTP (puerto 80)

El ESP expone un **servidor HTTP** en el puerto 80. Solo accesible desde clientes en la red `192.168.50.x`.

| Campo | Valor |
|-------|-------|
| **URL base** | `http://192.168.50.1` |
| **Puerto** | 80 |
| **Content-Type** | `application/json` |

### Endpoints

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/api/wifi` | Lista redes WiFi visibles y la conectada |
| POST | `/api/wifi` | Conecta el ESP a una red (body: `{"ssid","password"}`) |
| * | * | 404 `{"error":"Not found"}` |

Ver `specs/API-WIFI-WEBSERVER-SPEC.md` para request/response detallados.

---

## 6. UDP Discovery (puerto 9999)

Además del HTTP, el ESP escucha y envía por **UDP puerto 9999**:

| Campo | Valor |
|-------|-------|
| **Puerto** | 9999 |
| **Escucha** | Unicast y broadcast en 9999 |
| **Broadcast proactivo** | Cada 2 s a `192.168.50.255:9999` y `255.255.255.255:9999` |
| **Mensaje discovery** | `AIA-DISCOVER` (para registrar subscriber) |

Los clientes en `192.168.50.x` reciben el broadcast y pueden enviar `AIA-DISCOVER` a `192.168.50.1:9999` para registrarse y recibir lecturas del sensor.

---

## 7. Resumen para la app

Para comunicarse con el sensor cuando el móvil está conectado al AP:

| Servicio | URL/Dirección |
|----------|---------------|
| **API WiFi (scan, conectar)** | `http://192.168.50.1/api/wifi` |
| **Discovery UDP** | Escuchar en puerto 9999; enviar `AIA-DISCOVER` a `192.168.50.1:9999` |
| **Lecturas del sensor** | Llegan por UDP al puerto desde el que se envió AIA-DISCOVER |
