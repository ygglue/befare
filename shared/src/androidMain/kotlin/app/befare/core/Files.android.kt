package app.befare.core

actual fun readFileBytes(path: String): ByteArray = java.io.File(path).readBytes()
