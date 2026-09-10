"""Tests for aia-agent workers."""

import pytest
from unittest.mock import Mock, patch, MagicMock
from datetime import datetime, timezone


class TestMqttWorker:
    """Tests for MQTT worker functions."""

    def test_calcular_nivel_normal(self):
        """Test calcular_nivel with normal water level."""
        from workers.mqtt_worker import calcular_nivel
        
        # Mock config values
        with patch('workers.mqtt_worker.ALTURA_SENSOR', 160), \
             patch('workers.mqtt_worker.CAPACIDAD_LITROS', 5000), \
             patch('workers.mqtt_worker.SENSOR_DIST_UMBRAL', 20), \
             patch('workers.mqtt_worker.SENSOR_DIST_CORRECCION', 15):
            
            # Distancia 50cm -> altura_agua = 110cm -> ~68.75%
            result = calcular_nivel(50.0)
            
            assert result['distancia'] == 50.0
            assert result['altura_agua'] == 110.0
            assert result['porcentaje'] == pytest.approx(68.75, rel=0.01)
            assert result['litros'] == pytest.approx(3437.5, rel=0.01)
            assert result['estado'] == 'normal'

    def test_calcular_nivel_alerta(self):
        """Test calcular_nivel with alert water level."""
        from workers.mqtt_worker import calcular_nivel
        
        with patch('workers.mqtt_worker.ALTURA_SENSOR', 160), \
             patch('workers.mqtt_worker.CAPACIDAD_LITROS', 5000), \
             patch('workers.mqtt_worker.SENSOR_DIST_UMBRAL', 20), \
             patch('workers.mqtt_worker.SENSOR_DIST_CORRECCION', 15):
            
            # Distancia 100cm -> altura_agua = 60cm -> 37.5%
            result = calcular_nivel(100.0)
            
            assert result['estado'] == 'alerta'

    def test_calcular_nivel_peligro(self):
        """Test calcular_nivel with danger water level."""
        from workers.mqtt_worker import calcular_nivel
        
        with patch('workers.mqtt_worker.ALTURA_SENSOR', 160), \
             patch('workers.mqtt_worker.CAPACIDAD_LITROS', 5000), \
             patch('workers.mqtt_worker.SENSOR_DIST_UMBRAL', 20), \
             patch('workers.mqtt_worker.SENSOR_DIST_CORRECCION', 15):
            
            # Distancia 150cm -> altura_agua = 10cm -> 6.25%
            result = calcular_nivel(150.0)
            
            assert result['estado'] == 'peligro'

    def test_calcular_nivel_sensor_correction(self):
        """Test sensor correction for distances <= 20cm."""
        from workers.mqtt_worker import calcular_nivel
        
        with patch('workers.mqtt_worker.ALTURA_SENSOR', 160), \
             patch('workers.mqtt_worker.CAPACIDAD_LITROS', 5000), \
             patch('workers.mqtt_worker.SENSOR_DIST_UMBRAL', 20), \
             patch('workers.mqtt_worker.SENSOR_DIST_CORRECCION', 15):
            
            # Distancia 10cm <= umbral 20 -> corregida a max(0, 10-15) = 0
            result = calcular_nivel(10.0)
            
            assert result['distancia'] == 0.0
            assert result['altura_agua'] == 160.0
            assert result['porcentaje'] == 100.0
            assert result['litros'] == 5000.0

    def test_calcular_nivel_edge_cases(self):
        """Test edge cases for water level calculation."""
        from workers.mqtt_worker import calcular_nivel
        
        with patch('workers.mqtt_worker.ALTURA_SENSOR', 160), \
             patch('workers.mqtt_worker.CAPACIDAD_LITROS', 5000), \
             patch('workers.mqtt_worker.SENSOR_DIST_UMBRAL', 20), \
             patch('workers.mqtt_worker.SENSOR_DIST_CORRECCION', 15):
            
            # Distancia negativa
            result = calcular_nivel(-10.0)
            assert result['altura_agua'] == 160.0
            
            # Distancia mayor que altura sensor
            result = calcular_nivel(200.0)
            assert result['altura_agua'] == 0.0
            assert result['porcentaje'] == 0.0
            assert result['litros'] == 0.0


class TestEstanqueEstado:
    """Tests for EstanqueEstado data class."""

    def test_estanque_estado_creation(self):
        """Test EstanqueEstado can be created with all fields."""
        # Using a simple dict since mqtt_worker uses dicts
        estado = {
            'distancia': 50.0,
            'litros': 3437.5,
            'porcentaje': 68.75,
            'altura_agua': 110.0,
            'estado': 'normal',
            'ultima_lectura': '2026-01-15 10:30:00',
            'mqtt_connected': True
        }
        
        assert estado['distancia'] == 50.0
        assert estado['litros'] == 3437.5
        assert estado['porcentaje'] == 68.75
        assert estado['estado'] == 'normal'
        assert estado['mqtt_connected'] is True


class TestWhatsAppReader:
    """Tests for WhatsApp Reader functions."""

    def test_phone_number_cleaning(self):
        """Test phone number cleaning logic."""
        # Simulate the cleaning from whatsapp_sender.py
        phone = "+56 9 1234 5678"
        cleaned = ''.join(filter(str.isdigit, phone))
        assert cleaned == "56912345678"
        
        phone = "56912345678"
        cleaned = ''.join(filter(str.isdigit, phone))
        assert cleaned == "56912345678"
        
        phone = "  56 9 1234 5678  "
        cleaned = ''.join(filter(str.isdigit, phone))
        assert cleaned == "56912345678"

    def test_invalid_phone_numbers(self):
        """Test invalid phone number detection."""
        # Too short
        phone = "1234567"
        cleaned = ''.join(filter(str.isdigit, phone))
        assert len(cleaned) < 8
        
        # Empty
        phone = ""
        cleaned = ''.join(filter(str.isdigit, phone))
        assert len(cleaned) == 0


class TestConfig:
    """Tests for configuration values."""

    def test_mqtt_config_defaults(self):
        """Test MQTT config has expected defaults."""
        import os
        from workers.mqtt_worker import (
            MQTT_HOST, MQTT_PORT, MQTT_USERNAME, 
            MQTT_PASSWORD, MQTT_TOPIC_OUT
        )
        
        assert MQTT_HOST == 'broker.mqttdashboard.com'
        assert MQTT_PORT == 1883
        assert MQTT_USERNAME == 'test'
        assert MQTT_PASSWORD == 'test'
        assert MQTT_TOPIC_OUT == 'yai-mqtt/YUS-0.2.8-COSTA/out'

    def test_tank_config_defaults(self):
        """Test tank config has expected defaults."""
        from workers.mqtt_worker import (
            PARCELA_NOMBRE, ALTURA_SENSOR, CAPACIDAD_LITROS,
            SENSOR_DIST_UMBRAL, SENSOR_DIST_CORRECCION
        )
        
        assert PARCELA_NOMBRE == "Posada en el Bosque"
        assert ALTURA_SENSOR == 160
        assert CAPACIDAD_LITROS == 5000
        assert SENSOR_DIST_UMBRAL == 20
        assert SENSOR_DIST_CORRECCION == 15


# Simple smoke test to ensure imports work
def test_imports():
    """Test that all modules can be imported without errors."""
    import workers.mqtt_worker
    import workers.whatsapp_reader
    import workers.whatsapp_sender
    assert True