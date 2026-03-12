#!/usr/bin/env python3
"""
MQTT Worker - Escucha MQTT y guarda promedios en MongoDB

Este script corre como Background Worker en Render.
Escucha la cola MQTT del sensor de estanque y guarda
el promedio cada 10 lecturas en MongoDB.
"""

import os
import time
from datetime import datetime, timezone
from collections import deque
from dotenv import load_dotenv
from pymongo import MongoClient
import paho.mqtt.client as mqtt
import threading
import dash
from dash import html, dcc, Input, Output
import plotly.graph_objs as go

# Cargar variables de entorno
load_dotenv()

# ============================================================
# CONFIGURACIÓN
# ============================================================

# MongoDB
MONGO_URI = os.getenv("MONGODB_URI", "")

# MQTT
MQTT_HOST = os.getenv('MQTT_HOST', 'broker.mqttdashboard.com')
MQTT_PORT = int(os.getenv('MQTT_PORT', '1883'))
MQTT_USERNAME = os.getenv('MQTT_USERNAME', 'test')
MQTT_PASSWORD = os.getenv('MQTT_PASSWORD', 'test')
MQTT_TOPIC_OUT = os.getenv('MQTT_TOPIC_OUT', 'yai-mqtt/YUS-0.2.8-COSTA/out')

# Configuración del estanque
PARCELA_NOMBRE = "Posada en el Bosque"
ALTURA_SENSOR = 160  # cm desde el fondo
CAPACIDAD_LITROS = 5000

# Buffer para promedio móvil de 10 lecturas
lecturas_buffer = deque(maxlen=10)

# Conexión MongoDB
mongo_client = None
historial_collection = None

# Variables globales para el dashboard
data_queue = deque(maxlen=100)  # Para gráfico: (litros, timestamp)
last_record = {"litros": 0, "timestamp": datetime.now()}
save_status = "Esperando"  # "Guardando", "Error", "Esperando"
save_rate_counter = 0
last_rate_time = time.time()

# ============================================================
# MONGODB
# ============================================================

def get_collection():
    """Obtiene la colección de historial de MongoDB."""
    global mongo_client, historial_collection
    
    if not MONGO_URI:
        print("⚠️ MONGODB_URI no configurado")
        return None
    
    if mongo_client is None:
        try:
            mongo_client = MongoClient(MONGO_URI, serverSelectionTimeoutMS=5000)
            mongo_client.admin.command('ping')
            db = mongo_client["tomi-db"]
            historial_collection = db["estanque-historial"]
            print("✅ MongoDB conectado")
        except Exception as e:
            print(f"❌ Error conectando MongoDB: {e}")
            return None
    
    return historial_collection


def guardar_en_mongodb(datos: dict):
    """Guarda un registro en MongoDB (hora_local como clave única)."""
    global save_status, save_rate_counter
    collection = get_collection()
    if collection is None:
        save_status = "Error"
        return False
    
    # Truncar a minuto para la clave (evita duplicados en el mismo minuto)
    hora_local = datetime.now().strftime("%Y-%m-%d %H:%M")
    
    registro = {
        "timestamp": datetime.now(timezone.utc),
        "hora_local": hora_local,
        "distancia": round(datos.get("distancia", 0), 2),
        "altura_agua": round(datos.get("altura_agua", 0), 2),
        "litros": round(datos.get("litros", 0), 2),
        "porcentaje": round(datos.get("porcentaje", 0), 2),
        "estado": datos.get("estado", ""),
        "muestras": datos.get("lecturas_en_buffer", 1),
        "sensor": MQTT_TOPIC_OUT,
        "ubicacion": PARCELA_NOMBRE,
        "origin": "mqtt-worker",
        "user_origin": "render-worker",
        "user_agent": "mqtt_worker.py"
    }
    
    try:
        # Upsert: actualiza si existe hora_local, inserta si no existe
        result = collection.update_one(
            {"hora_local": hora_local},
            {"$set": registro},
            upsert=True
        )
        
        if result.upserted_id:
            print(f"📊 MongoDB [nuevo]: {hora_local} - {registro['porcentaje']:.1f}%")
        else:
            print(f"📊 MongoDB [actualizado]: {hora_local} - {registro['porcentaje']:.1f}%")
        save_status = "Guardando"
        save_rate_counter += 1
        return True
    except Exception as e:
        print(f"❌ Error guardando en MongoDB: {e}")
        save_status = "Error"
        return False


# ============================================================
# CÁLCULOS
# ============================================================

def calcular_nivel(distancia_sensor: float) -> dict:
    """Calcula litros y porcentaje basándose en la distancia del sensor."""
    altura_agua = ALTURA_SENSOR - distancia_sensor
    if altura_agua < 0:
        altura_agua = 0
    if altura_agua > ALTURA_SENSOR:
        altura_agua = ALTURA_SENSOR
    
    porcentaje = (altura_agua / ALTURA_SENSOR) * 100
    litros = (altura_agua / ALTURA_SENSOR) * CAPACIDAD_LITROS
    
    if distancia_sensor > 140:
        estado_nivel = "peligro"
    elif distancia_sensor > 80:
        estado_nivel = "alerta"
    else:
        estado_nivel = "normal"
    
    return {
        "distancia": distancia_sensor,
        "altura_agua": altura_agua,
        "litros": litros,
        "porcentaje": porcentaje,
        "estado": estado_nivel
    }


# ============================================================
# MQTT
# ============================================================

def on_connect(client, userdata, flags, reason_code, properties):
    """Callback cuando se conecta al broker MQTT."""
    if reason_code == 0:
        print(f"✅ MQTT Conectado a {MQTT_HOST}")
        client.subscribe(MQTT_TOPIC_OUT)
        print(f"📡 Suscrito a: {MQTT_TOPIC_OUT}")
    else:
        print(f"❌ Error MQTT: {reason_code}")


def on_disconnect(client, userdata, flags, reason_code, properties):
    """Callback cuando se desconecta del broker MQTT."""
    print(f"⚠️ MQTT Desconectado: {reason_code}")


def on_message(client, userdata, msg):
    """Callback cuando llega un mensaje MQTT."""
    global lecturas_buffer, last_record
    
    try:
        payload = msg.payload.decode('utf-8').strip()
        
        # Formato: YUS-0.2.8-COSTA,OKO,88.75,2026-03-07...
        partes = payload.split(',')
        
        if len(partes) >= 3 and "OKO" in partes[1]:
            distancia_raw = float(partes[2])
            
            # Agregar al buffer
            lecturas_buffer.append(distancia_raw)
            
            # Calcular promedio
            distancia_promedio = sum(lecturas_buffer) / len(lecturas_buffer)
            
            # Calcular nivel
            datos = calcular_nivel(distancia_promedio)
            datos["distancia_raw"] = distancia_raw
            datos["lecturas_en_buffer"] = len(lecturas_buffer)
            
            # Actualizar dashboard
            timestamp = datetime.now()
            data_queue.append((datos["litros"], timestamp))
            last_record = {"litros": datos["litros"], "timestamp": timestamp}
            
            print(f"💧 Nivel: {datos['litros']:.0f}L ({datos['porcentaje']:.1f}%) - Buffer: {len(lecturas_buffer)}/10")
            
            # Guardar en MongoDB cuando el buffer tiene 10 lecturas
            if len(lecturas_buffer) == 10:
                guardar_en_mongodb(datos)
                
    except Exception as e:
        print(f"❌ Error procesando mensaje: {e}")


# ============================================================
# DASHBOARD
# ============================================================

app = dash.Dash(__name__)

app.layout = html.Div([
    html.H1("Dashboard MQTT - aia-agent"),
    dcc.Graph(id='live-graph'),
    html.Div([
        html.H3("Estado de Guardado en MongoDB"),
        html.Div(id='save-indicator'),
        html.P(id='save-rate'),
    ], style={'position': 'absolute', 'right': '10px', 'top': '50px', 'width': '250px'}),
    html.Div([
        html.H3("Último Registro"),
        html.P(id='last-record'),
    ], style={'textAlign': 'center', 'marginTop': '20px'}),
    dcc.Interval(id='interval-component', interval=1000, n_intervals=0)
])

@app.callback(
    [Output('live-graph', 'figure'), Output('save-indicator', 'style'), Output('save-rate', 'children'), Output('last-record', 'children')],
    [Input('interval-component', 'n_intervals')]
)
def update_dashboard(n):
    global save_rate_counter, last_rate_time, save_status
    # Gráfico
    lts_values = [d[0] for d in data_queue]
    times = [d[1] for d in data_queue]
    figure = go.Figure(data=[go.Scatter(x=times, y=lts_values, mode='lines')])
    figure.update_layout(title='Litros vs Tiempo', xaxis_title='Tiempo', yaxis_title='Litros')
    
    # Indicador
    indicator_style = {'backgroundColor': 'green' if save_status == "Guardando" else 'red', 'width': '50px', 'height': '50px', 'borderRadius': '50%', 'margin': 'auto'}
    
    # Tasa (calcula por minuto, ya que guarda cada 10 lecturas)
    current_time = time.time()
    elapsed = current_time - last_rate_time
    if elapsed >= 60:
        rate = (save_rate_counter / elapsed) * 60  # por minuto
        rate_text = f"Tasa de guardado: {rate:.2f} registros/min"
        save_rate_counter = 0
        last_rate_time = current_time
    else:
        rate_text = f"Tasa de guardado: Calculando..."
    
    # Último registro
    last_text = f"Litros: {last_record['litros']:.0f}, Fecha: {last_record['timestamp'].strftime('%Y-%m-%d %H:%M:%S')}"
    
    return figure, indicator_style, rate_text, last_text

def run_dashboard():
    app.run_server(debug=False, use_reloader=False, host='0.0.0.0', port=8050)


# ============================================================
# MAIN
# ============================================================

def main():
    """Función principal del worker."""
    print("🚀 Iniciando MQTT Worker...")
    print(f"   MQTT: {MQTT_HOST}:{MQTT_PORT}")
    print(f"   Topic: {MQTT_TOPIC_OUT}")
    print(f"   MongoDB: {'Configurado' if MONGO_URI else 'No configurado'}")
    print("")
    
    # Verificar conexión a MongoDB al inicio
    get_collection()
    
    # Iniciar dashboard en hilo separado
    dashboard_thread = threading.Thread(target=run_dashboard)
    dashboard_thread.daemon = True
    dashboard_thread.start()
    print("📊 Dashboard iniciado en http://localhost:8050")
    
    try:
        while True:
            try:
                client = mqtt.Client()  # Cambia a sin CallbackAPIVersion
                client.username_pw_set(MQTT_USERNAME, MQTT_PASSWORD)
                client.on_connect = on_connect
                client.on_disconnect = on_disconnect
                client.on_message = on_message
                
                print(f"🔌 Conectando a MQTT...")
                client.connect(MQTT_HOST, MQTT_PORT, 60)
                client.loop_forever()
                
            except Exception as e:
                print(f"❌ Error: {e} - Reintentando en 5s...")
                time.sleep(5)
    except KeyboardInterrupt:
        print("\n👋 Worker detenido por usuario")
        # Cerrar conexión MongoDB explícitamente
        global mongo_client
        if mongo_client:
            mongo_client.close()
            print("✅ Conexión MongoDB cerrada")

if __name__ == "__main__":
    main()
