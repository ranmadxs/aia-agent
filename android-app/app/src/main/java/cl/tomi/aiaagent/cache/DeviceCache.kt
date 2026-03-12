package cl.tomi.aiaagent.cache

import android.content.Context
import android.content.SharedPreferences
import cl.tomi.aiaagent.data.NetworkDeviceItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

private const val PREFS_NAME = "aia_agent_device_cache"
private const val KEY_DEVICES = "cached_devices"
private const val MAX_CACHED = 20

/**
 * Cache de dispositivos descubiertos para que no desaparezcan al volver atrás.
 * Persiste en SharedPreferences.
 */
class DeviceCache(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<NetworkDeviceItem>>() {}.type

    fun loadDevices(): List<NetworkDeviceItem> {
        val json = prefs.getString(KEY_DEVICES, null) ?: return emptyList()
        return try {
            @Suppress("UNCHECKED_CAST")
            val list = (gson.fromJson(json, listType) as? List<NetworkDeviceItem>) ?: emptyList()
            list.distinctBy { it.businessKey }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveDevice(device: NetworkDeviceItem) {
        val current = loadDevices().toMutableList()
        val existing = current.indexOfFirst { it.businessKey == device.businessKey }
        if (existing >= 0) {
            current[existing] = device
        } else {
            current.add(0, device)
        }
        val toSave = current.distinctBy { it.businessKey }.take(MAX_CACHED)
        prefs.edit().putString(KEY_DEVICES, gson.toJson(toSave)).apply()
    }

    fun saveDevices(devices: List<NetworkDeviceItem>) {
        val distinct = devices.distinctBy { it.businessKey }.take(MAX_CACHED)
        prefs.edit().putString(KEY_DEVICES, gson.toJson(distinct)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_DEVICES).apply()
    }
}
