#!/usr/bin/env python3
"""
WhatsApp Reader - Lee mensajes de un chat específico

Worker que:
- Abre WhatsApp Web con Selenium
- Lee mensajes de un canal/chat específico
- Expone una API web con el estado y mensajes
"""

import os
import time
import threading
from datetime import datetime
from collections import deque
from dotenv import load_dotenv
from flask import Flask, jsonify, render_template_string
from selenium import webdriver
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from webdriver_manager.chrome import ChromeDriverManager

load_dotenv()

# ============================================================
# CONFIGURACIÓN
# ============================================================

WHATSAPP_CHAT = os.getenv('WHATSAPP_CHAT', '')  # Nombre del chat a monitorear
WEB_PORT = int(os.getenv('READER_PORT', '5001'))
PROFILE_PATH = os.path.expanduser('~/.whatsapp_reader_profile')

# Estado global
estado = {
    "conectado": False,
    "chat_actual": None,
    "ultimo_mensaje": None,
    "mensajes_leidos": 0,
    "ultimo_check": None,
    "error": None
}

# Historial de mensajes (últimos 100)
mensajes = deque(maxlen=100)

# Driver global
driver = None

# ============================================================
# FLASK APP (Estado Web)
# ============================================================

app = Flask(__name__)

HTML_TEMPLATE = """
<!DOCTYPE html>
<html>
<head>
    <title>📱 WhatsApp Reader</title>
    <meta http-equiv="refresh" content="10">
    <style>
        body { font-family: -apple-system, sans-serif; background: #0a1628; color: #fff; padding: 20px; }
        .container { max-width: 800px; margin: 0 auto; }
        h1 { color: #25D366; }
        .status { background: #1e293b; padding: 20px; border-radius: 10px; margin-bottom: 20px; }
        .status-item { margin: 10px 0; }
        .connected { color: #4ade80; }
        .disconnected { color: #ef4444; }
        .messages { background: #1e293b; padding: 20px; border-radius: 10px; }
        .message { background: #0f172a; padding: 10px; margin: 5px 0; border-radius: 5px; }
        .message-time { color: #64748b; font-size: 0.8em; }
        .message-sender { color: #25D366; font-weight: bold; }
    </style>
</head>
<body>
    <div class="container">
        <h1>📱 WhatsApp Reader</h1>
        <div class="status">
            <div class="status-item">
                <strong>Estado:</strong> 
                <span class="{{ 'connected' if estado.conectado else 'disconnected' }}">
                    {{ '🟢 Conectado' if estado.conectado else '🔴 Desconectado' }}
                </span>
            </div>
            <div class="status-item"><strong>Chat:</strong> {{ estado.chat_actual or 'No configurado' }}</div>
            <div class="status-item"><strong>Mensajes leídos:</strong> {{ estado.mensajes_leidos }}</div>
            <div class="status-item"><strong>Último check:</strong> {{ estado.ultimo_check or '-' }}</div>
            {% if estado.error %}
            <div class="status-item"><strong>Error:</strong> {{ estado.error }}</div>
            {% endif %}
        </div>
        <div class="messages">
            <h3>Últimos mensajes</h3>
            {% for msg in mensajes %}
            <div class="message">
                <div class="message-sender">{{ msg.sender }}</div>
                <div>{{ msg.text }}</div>
                <div class="message-time">{{ msg.time }}</div>
            </div>
            {% else %}
            <p>No hay mensajes aún</p>
            {% endfor %}
        </div>
    </div>
</body>
</html>
"""

@app.route('/')
def home():
    return render_template_string(HTML_TEMPLATE, estado=estado, mensajes=list(mensajes)[-10:])

@app.route('/api/status')
def api_status():
    return jsonify(estado)

@app.route('/api/messages')
def api_messages():
    return jsonify(list(mensajes))


# ============================================================
# SELENIUM WHATSAPP
# ============================================================

def iniciar_driver():
    """Inicia el driver de Chrome con perfil persistente."""
    global driver
    
    options = Options()
    options.add_argument(f'--user-data-dir={PROFILE_PATH}')
    options.add_argument('--no-sandbox')
    options.add_argument('--disable-dev-shm-usage')
    
    service = Service(ChromeDriverManager().install())
    driver = webdriver.Chrome(service=service, options=options)
    driver.get('https://web.whatsapp.com')
    
    print("🌐 WhatsApp Web abierto")
    print("📱 Escanea el QR si es la primera vez")
    
    return driver


def esperar_carga():
    """Espera a que WhatsApp Web cargue completamente."""
    global estado
    
    try:
        WebDriverWait(driver, 60).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, '[data-icon="chat"]'))
        )
        estado["conectado"] = True
        estado["error"] = None
        print("✅ WhatsApp Web conectado")
        return True
    except Exception as e:
        estado["conectado"] = False
        estado["error"] = str(e)
        print(f"❌ Error esperando carga: {e}")
        return False


def buscar_chat(nombre_chat: str):
    """Busca y abre un chat específico."""
    global estado
    
    try:
        # Buscar en la lista de chats
        search_box = WebDriverWait(driver, 10).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, '[data-icon="search"]'))
        )
        search_box.click()
        time.sleep(0.5)
        
        # Escribir nombre del chat
        search_input = driver.find_element(By.CSS_SELECTOR, '[contenteditable="true"]')
        search_input.clear()
        search_input.send_keys(nombre_chat)
        time.sleep(1)
        
        # Click en el primer resultado
        chat = WebDriverWait(driver, 5).until(
            EC.presence_of_element_located((By.XPATH, f'//span[@title="{nombre_chat}"]'))
        )
        chat.click()
        
        estado["chat_actual"] = nombre_chat
        print(f"💬 Chat abierto: {nombre_chat}")
        return True
        
    except Exception as e:
        estado["error"] = f"No se encontró el chat: {nombre_chat}"
        print(f"❌ Error buscando chat: {e}")
        return False


def leer_mensajes():
    """Lee los mensajes del chat actual."""
    global estado, mensajes
    
    try:
        # Buscar contenedor de mensajes
        msg_container = driver.find_elements(By.CSS_SELECTOR, '[data-id]')
        
        nuevos = 0
        for msg_elem in msg_container[-20:]:  # Últimos 20
            try:
                # Intentar extraer texto
                text_elem = msg_elem.find_elements(By.CSS_SELECTOR, '.selectable-text')
                if not text_elem:
                    continue
                    
                text = text_elem[0].text
                msg_id = msg_elem.get_attribute('data-id')
                
                # Verificar si ya lo tenemos
                if any(m.get('id') == msg_id for m in mensajes):
                    continue
                
                # Extraer remitente si es grupo
                sender = "Desconocido"
                sender_elem = msg_elem.find_elements(By.CSS_SELECTOR, '._amk6')
                if sender_elem:
                    sender = sender_elem[0].text
                
                mensaje = {
                    "id": msg_id,
                    "text": text,
                    "sender": sender,
                    "time": datetime.now().strftime("%H:%M:%S"),
                    "timestamp": datetime.now().isoformat()
                }
                
                mensajes.append(mensaje)
                nuevos += 1
                print(f"📩 [{sender}]: {text[:50]}...")
                
            except Exception:
                pass
        
        estado["mensajes_leidos"] += nuevos
        estado["ultimo_check"] = datetime.now().strftime("%H:%M:%S")
        
    except Exception as e:
        estado["error"] = str(e)
        print(f"❌ Error leyendo mensajes: {e}")


def worker_loop():
    """Loop principal del worker."""
    global estado
    
    print("🚀 Iniciando WhatsApp Reader...")
    
    try:
        iniciar_driver()
        
        if not esperar_carga():
            return
        
        if WHATSAPP_CHAT:
            if not buscar_chat(WHATSAPP_CHAT):
                print("⚠️ Continuando sin chat específico")
        
        print("👀 Monitoreando mensajes...")
        
        while True:
            try:
                leer_mensajes()
                time.sleep(3)  # Revisar cada 3 segundos
            except KeyboardInterrupt:
                break
            except Exception as e:
                estado["error"] = str(e)
                print(f"❌ Error en loop: {e}")
                time.sleep(5)
                
    except Exception as e:
        print(f"❌ Error fatal: {e}")
    finally:
        if driver:
            driver.quit()


def main():
    """Función principal."""
    print(f"📱 WhatsApp Reader")
    print(f"   Chat: {WHATSAPP_CHAT or 'No especificado'}")
    print(f"   Web: http://localhost:{WEB_PORT}")
    print("")
    
    # Iniciar worker en thread separado
    worker_thread = threading.Thread(target=worker_loop, daemon=True)
    worker_thread.start()
    
    # Iniciar servidor web
    app.run(host='0.0.0.0', port=WEB_PORT, debug=False)


if __name__ == "__main__":
    main()
