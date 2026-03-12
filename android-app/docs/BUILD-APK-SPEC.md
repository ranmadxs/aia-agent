# Spec: Crear APK de AIA Agent

Guía para aumentar la versión (SemVer) y compilar el APK.

---

## 1. Aumentar la versión (SemVer)

Antes de compilar, actualiza la versión en `app/build.gradle.kts`.

### SemVer (Semantic Versioning)

Formato: `MAJOR.MINOR.PATCH`

| Parte   | Cuándo aumentar | Ejemplo |
|---------|-----------------|---------|
| **MAJOR** | Cambios incompatibles con versiones anteriores | 2.0.0 |
| **MINOR** | Nueva funcionalidad compatible | 1.1.0 |
| **PATCH** | Correcciones de bugs compatibles | 1.0.22 |

### Editar `app/build.gradle.kts`

```kotlin
val appVersionName = "1.0.22"   // ← Subir según SemVer
defaultConfig {
    ...
    versionCode = 23            // ← Incrementar en 1 (entero, nunca bajar)
    versionName = appVersionName
    ...
}
```

**Reglas:**
- `appVersionName`: sigue SemVer (ej: 1.0.21 → 1.0.22 para patch)
- `versionCode`: entero que sube en cada release (21 → 22 → 23...). Google Play lo usa para ordenar versiones.

---

## 2. Compilar y crear el APK

### Debug (desarrollo, firmado automáticamente)

```bash
cd android-app
./gradlew assembleDebug
```

APK generado: `app/build/outputs/apk/debug/aia-agent-vX.Y.Z-debug.apk`

### Release (producción, sin firmar por defecto)

```bash
cd android-app
./gradlew assembleRelease
```

APK generado: `app/build/outputs/apk/release/aia-agent-vX.Y.Z-release-unsigned.apk`

### Instalar en dispositivo conectado (debug)

```bash
./gradlew installDebug
```

---

## 3. Si falla "Unable to locate a Java Runtime"

En macOS con Homebrew, configura `JAVA_HOME` antes de ejecutar Gradle:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew assembleDebug
```

O añade esa línea a `~/.zshrc` para que sea permanente.

---

## 4. Resumen rápido

1. Editar `app/build.gradle.kts`: subir `appVersionName` (SemVer) y `versionCode` (+1)
2. Ejecutar `./gradlew assembleDebug` (o `assembleRelease`)
3. APK en `app/build/outputs/apk/debug/` o `release/`
