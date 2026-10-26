package com.example.link

/**
 * Limitador de tasa (Rate Limiter) con ventana deslizante de 60 segundos.
 * Limita los comandos de escritura a un máximo de 30 por minuto en total.
 */
class RateLimiter(private val maxPerMinute: Int = 30) {

    private val timestamps = ArrayDeque<Long>()
    private val lock = Any()

    /**
     * Intenta adquirir un permiso de ejecución.
     * @param nowMs Marca de tiempo actual en milisegundos (por defecto System.currentTimeMillis()).
     * @return true si el comando está permitido dentro de la tasa; false si debe rechazarse con RATE_LIMITED.
     */
    fun tryAcquire(nowMs: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        val windowStart = nowMs - 60_000L
        while (timestamps.isNotEmpty() && timestamps.first() < windowStart) {
            timestamps.removeFirst()
        }
        return if (timestamps.size < maxPerMinute) {
            timestamps.addLast(nowMs)
            true
        } else {
            false
        }
    }

    /**
     * Devuelve la cantidad de ejecuciones actualmente en la ventana activa.
     */
    fun currentCount(nowMs: Long = System.currentTimeMillis()): Int = synchronized(lock) {
        val windowStart = nowMs - 60_000L
        while (timestamps.isNotEmpty() && timestamps.first() < windowStart) {
            timestamps.removeFirst()
        }
        timestamps.size
    }

    /**
     * Limpia la ventana de marcas de tiempo.
     */
    fun reset() = synchronized(lock) {
        timestamps.clear()
    }
}
