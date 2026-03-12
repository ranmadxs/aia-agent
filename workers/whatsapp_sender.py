#!/usr/bin/env python3
"""
WhatsApp Sender - Envía mensajes a números específicos

Worker que:
- Abre WhatsApp Web con Selenium
- Expone una API para enviar mensajes
- Permite enviar a cualquier número
"""

import os
import time
import urllib.parse
from datetime import datetime
from collections import deque
from dotenv import load_dotenv
from flask import Flask, jsonify, request, render_template_string
from selenium import webdriver
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from webdriver_manager.chrome import ChromeDriverManager

load_dotenv()

# ============================================================
# CONFIGURACIÓN
# ============================================================

WEB_PORT = int(os.getenv('SENDER_PORT', '5002'))
PROFILE_PATH = os.path.expanduser('~/.whatsapp_sender_profile')

# Estado global
estado = {
    "conectado": False,
    "qr_escaneado": False,
    "mensajes_enviados": 0,
    "ultimo_envio": None,
    "error": None
}

# Historial de envíos (últimos 50)
historial_envios = deque(maxlen=50)

# Driver global
driver = None

# ============================================================
# FLASK APP
# ============================================================

app = Flask(__name__)

HTML_TEMPLATE = """
<!DOCTYPE html>
<html>
<head>
    <title>📤 WhatsApp Sender</title>
    <style>
        body { font-family: -apple-system, sans-serif; background: #0a1628; color: #fff; padding: 20px; }
        .container { max-width: 800px; margin: 0 auto; }
        h1 { color: #25D366; }
        .status { background: #1e293b; padding: 20px; border-radius: 10px; margin-bottom: 20px; }
        .connected { color: #4ade80; }
        .disconnected { color: #ef4444; }
        .form-section { background: #1e293b; padding: 20px; border-radius: 10px; margin-bottom: 20px; }
        input, textarea { width: 100%; padding: 10px; margin: 10px 0; border: none; border-radius: 5px; background: #0f172a; color: #fff; }
        button { background: #25D366; color: #fff; padding: 15px 30px; border: none; border-radius: 5px; cursor: pointer; font-size: 1em; }
        button:hover { background: #128C7E; }
        .history { background: #1e293b; padding: 20px; border-radius: 10px; }
        .history-item { background: #0f172a; padding: 10px; margin: 5px 0; border-radius: 5px; }
        .success { border-left: 3px solid #4ade80; }
        .failed { border-left: 3px solid #ef4444; }
        .time { color: #64748b; font-size: 0.8em; }
    </style>
</head>
<body>
    <div class="container">
        <h1>📤 WhatsApp Sender</h1>
        
        <div class="status">
            <p><strong>Estado:</strong> 
                <span class="{{ 'connected' if estado.conectado else 'disconnected' }}">
                    {{ '🟢 Conectado' if estado.conectado else '🔴 Desconectado' }}
                </span>
            </p>
            <p><strong>Mensajes enviados:</strong> {{ estado.mensajes_enviados }}</p>
            <p><strong>Último envío:</strong> {{ estado.ultimo_envio or '-' }}</p>
            {% if estado.error %}
            <p><strong>Error:</strong> {{ estado.error }}</p>
            {% endif %}
        </div>
        
        <div class="form-section">
            <h3>Enviar mensaje</h3>
            <form action="/api/send" method="POST">
                <input type="text" name="phone" placeholder="Número (ej: 56912345678)" required>
                <textarea name="message" placeholder="Mensaje" rows="3" required></textarea>
                <button type="submit">📤 Enviar</button>
            </form>
        </div>
        
        <div class="history">
            <h3>Historial de envíos</h3>
            {% for item in historial %}
            <div class="history-item {{ 'success' if item.success else 'failed' }}">
                <strong>{{ item.phone }}</strong>
                <p>{{ item.message[:100] }}...</p>
                <span class="time">{{ item.time }} - {{ '✅ Enviado' if item.success else '❌ Falló' }}</span>
            </div>
            {% else %}
            <p>No hay envíos aún</p>
            {% endfor %}
        </div>
    </div>
</body>
</html>
"""

@app.route('/')
def home():
    return render_template_string(HTML_TEMPLATE, estado=estado, historial=list(historial_envios)[-10:])

@app.route('/api/status')
def api_status():
    return jsonify(estado)

@app.route('/api/send', methods=['POST'])
def api_send():
    """Envía un mensaje a un número."""
    data = request.form if request.form else request.json
    
    phone = data.get('phone', '').strip()
    message = data.get('message', '').strip()
    
    if not phone or not message:
        return jsonify({"error": "phone y message son requeridos"}), 400
    
    # Limpiar número (solo dígitos)
    phone = ''.join(filter(str.isdigit, phone))
    
    if len(phone) < 8:
        return jsonify({"error": "Número inválido"}), 400
    
    resultado = enviar_mensaje(phone, message)
    
    # Si es form, redirigir a home
    if request.form:
        from flask import redirect
        return redirect('/')
    
    return jsonify(resultado)

@app.route('/api/history')
def api_history():
    return jsonify(list(historial_envios))


# ============================================================
# SELENIUM WHATSAPP
# ============================================================

def iniciar_driver():
    """Inicia el driver de Chrome."""
    global driver, estado
    
    if driver is not None:
        return driver
    
    options = Options()
    options.add_argument(f'--user-data-dir={PROFILE_PATH}')
    options.add_argument('--no-sandbox')
    options.add_argument('--disable-dev-shm-usage')
    
    service = Service(ChromeDriverManager().install())
    driver = webdriver.Chrome(service=service, options=options)
    driver.get('https://web.whatsapp.com')
    
    print("🌐 WhatsApp Web abierto")
    print("📱 Escanea el QR si es la primera vez")
    
    # Esperar a que cargue
    try:
        WebDriverWait(driver, 120).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, '[data-icon="chat"]'))
        )
        estado["conectado"] = True
        estado["qr_escaneado"] = True
        estado["error"] = None
        print("✅ WhatsApp Web conectado")
    except Exception as e:
        estado["error"] = "Timeout esperando QR"
        print(f"❌ Error: {e}")
    
    return driver


def enviar_mensaje(phone: str, message: str) -> dict:
    """Envía un mensaje a un número específico."""
    global estado
    
    resultado = {
        "phone": phone,
        "message": message,
        "success": False,
        "time": datetime.now().strftime("%H:%M:%S"),
        "error": None
    }
    
    try:
        if driver is None or not estado["conectado"]:
            resultado["error"] = "WhatsApp no conectado"
            historial_envios.append(resultado)
            return resultado
        
        # Usar URL directa para abrir chat
        url = f'https://web.whatsapp.com/send?phone={phone}&text={urllib.parse.quote(message)}'
        driver.get(url)
        
        # Esperar que cargue el chat
        time.sleep(3)
        
        # Verificar si hay error de número inválido
        try:
            error_elem = driver.find_element(By.CSS_SELECTOR, '[data-icon="alert-phone"]')
            if error_elem:
                resultado["error"] = "Número no tiene WhatsApp"
                historial_envios.append(resultado)
                return resultado
        except:
            pass
        
        # Esperar botón de enviar
        send_button = WebDriverWait(driver, 15).until(
            EC.element_to_be_clickable((By.CSS_SELECTOR, '[data-icon="send"]'))
        )
        
        # Click para enviar
        send_button.click()
        time.sleep(2)
        
        resultado["success"] = True
        estado["mensajes_enviados"] += 1
        estado["ultimo_envio"] = datetime.now().strftime("%H:%M:%S")
        estado["error"] = None
        
        print(f"✅ Mensaje enviado a {phone}")
        
    except Exception as e:
        resultado["error"] = str(e)
        estado["error"] = str(e)
        print(f"❌ Error enviando a {phone}: {e}")
    
    historial_envios.append(resultado)
    return resultado


def main():
    """Función principal."""
    print(f"📤 WhatsApp Sender")
    print(f"   Web: http://localhost:{WEB_PORT}")
    print("")
    
    # Iniciar driver en thread separado para no bloquear
    import threading
    driver_thread = threading.Thread(target=iniciar_driver, daemon=True)
    driver_thread.start()
    
    # Iniciar servidor web
    app.run(host='0.0.0.0', port=WEB_PORT, debug=False, threaded=True)


if __name__ == "__main__":
    main()
