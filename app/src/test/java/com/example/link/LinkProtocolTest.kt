package com.example.link

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LinkProtocolTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testSecret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    // -------------------------------------------------------------
    // 1. FIRMA Y VERIFICACIÓN
    // -------------------------------------------------------------

    @Test
    fun testSignAndVerifyCommandEnvelope() {
        val payload = """{"modo":"crear_en_panel","usuario":"cliente1","password":"SecretPassword1","vence":"2026-11-25"}"""
        val id = "cmd-12345"
        val ts = 1767000000L
        val cmd = "ADD_USER"
        val signable = "1|$id|$ts|$cmd|$payload"
        val sig = LinkProtocol.sign(testSecret, signable)

        val envelope = LinkEnvelope(
            v = 1,
            id = id,
            ts = ts,
            cmd = cmd,
            payload = payload,
            sig = sig
        )

        // Verificación correcta
        assertTrue("La firma válida debe ser aceptada", LinkProtocol.verify(envelope, testSecret))

        // Secreto incorrecto debe fallar
        val wrongSecret = "wrong_secret_12345678901234567890123456789012"
        assertFalse("Un secreto incorrecto debe rechazar la firma", LinkProtocol.verify(envelope, wrongSecret))

        // Modificación de 1 solo carácter en el payload debe invalidar
        val tamperedPayload = envelope.copy(payload = """{"modo":"crear_en_panel","usuario":"cliente2","password":"SecretPassword1","vence":"2026-11-25"}""")
        assertFalse("Un payload alterado debe invalidar la firma", LinkProtocol.verify(tamperedPayload, testSecret))

        // Modificación en cmd o id
        assertFalse("Un id alterado debe invalidar la firma", LinkProtocol.verify(envelope.copy(id = "cmd-99999"), testSecret))
        assertFalse("Un cmd alterado debe invalidar la firma", LinkProtocol.verify(envelope.copy(cmd = "DELETE_USER"), testSecret))
    }

    @Test
    fun testBuildAndVerifyResponseEnvelope() {
        val cmdId = "orig-cmd-5678"
        val response = LinkProtocol.buildResponse(
            re = cmdId,
            ok = true,
            code = LinkCodes.OK,
            msg = "Usuario creado exitosamente",
            data = mapOf("id" to "900123", "usuario" to "cliente1"),
            secret = testSecret
        )

        assertEquals("orig-cmd-5678", response.re)
        assertNull(response.cmd)
        assertTrue(response.payload.contains("OK"))

        // La respuesta debe verificar correctamente con el secreto
        assertTrue("La respuesta generada debe tener firma válida", LinkProtocol.verify(response, testSecret))

        // Si se altera la firma, debe fallar
        val tamperedResponse = response.copy(sig = response.sig.reversed())
        assertFalse("Firma invertida debe fallar", LinkProtocol.verify(tamperedResponse, testSecret))
    }

    // -------------------------------------------------------------
    // 2. TTL (isFresh)
    // -------------------------------------------------------------

    @Test
    fun testTtlForPingAndListUsers() {
        val now = 1767000000L

        // PING / LIST_USERS: TTL = 120s
        assertTrue(LinkProtocol.isFresh("PING", now, now))
        assertTrue(LinkProtocol.isFresh("PING", now - 119L, now))
        assertTrue(LinkProtocol.isFresh("PING", now - 120L, now))
        assertFalse("PING a 121 segundos debe expirar", LinkProtocol.isFresh("PING", now - 121L, now))

        assertTrue(LinkProtocol.isFresh("LIST_USERS", now - 60L, now))
        assertFalse("LIST_USERS a 125 segundos debe expirar", LinkProtocol.isFresh("LIST_USERS", now - 125L, now))
    }

    @Test
    fun testTtlForUserOperations() {
        val now = 1767000000L

        // ADD_USER, EXTEND_USER, DELETE_USER: TTL = 24h (86400s)
        assertTrue(LinkProtocol.isFresh("ADD_USER", now - 3600L, now)) // 1 hora atrás
        assertTrue(LinkProtocol.isFresh("ADD_USER", now - 86400L, now)) // 24 horas exactas
        assertFalse("ADD_USER pasadas 24h + 1s debe expirar", LinkProtocol.isFresh("ADD_USER", now - 86401L, now))

        assertTrue(LinkProtocol.isFresh("EXTEND_USER", now - 40000L, now))
        assertTrue(LinkProtocol.isFresh("DELETE_USER", now - 86400L, now))
        assertFalse(LinkProtocol.isFresh("DELETE_USER", now - 90000L, now))
    }

    // -------------------------------------------------------------
    // 3. VALIDADORES (Casos límite)
    // -------------------------------------------------------------

    @Test
    fun testValidatePasswordEdgeCases() {
        // Caso límite: 7 caracteres -> debe fallar
        val pass7 = "Abc1234"
        val res7 = LinkProtocol.validatePassword(pass7)
        assertFalse("7 caracteres debe fallar", res7.isValid)
        assertEquals(LinkCodes.INVALID_PASSWORD, res7.errorCode)

        // Caso límite: 8 caracteres -> debe pasar
        val pass8 = "Abc12345"
        val res8 = LinkProtocol.validatePassword(pass8)
        assertTrue("8 caracteres debe ser válido", res8.isValid)

        // Caso límite: 32 caracteres -> debe pasar
        val pass32 = "A".repeat(32)
        val res32 = LinkProtocol.validatePassword(pass32)
        assertTrue("32 caracteres debe ser válido", res32.isValid)

        // Caso límite: 33 caracteres -> debe fallar
        val pass33 = "A".repeat(33)
        val res33 = LinkProtocol.validatePassword(pass33)
        assertFalse("33 caracteres debe fallar", res33.isValid)

        // Caso límite: espacios en blanco -> debe fallar
        val passWithSpace = "Pass 1234"
        val resSpace = LinkProtocol.validatePassword(passWithSpace)
        assertFalse("Contraseña con espacios debe fallar", resSpace.isValid)
        assertEquals(LinkCodes.INVALID_PASSWORD, resSpace.errorCode)

        val passWithLeadingSpace = " Pass1234"
        assertFalse("Espacio al inicio debe fallar", LinkProtocol.validatePassword(passWithLeadingSpace).isValid)

        // Caracteres no imprimibles / fuera de 0x21..0x7E -> debe fallar
        val passNonAscii = "Password123ñ"
        assertFalse("Contraseña con ñ (no ASCII) debe fallar", LinkProtocol.validatePassword(passNonAscii).isValid)
    }

    @Test
    fun testValidateUsernameEdgeCases() {
        // Test0001 con y sin origen=prueba
        val testUser = "Test0001"

        // Sin origen o con origen != prueba -> debe fallar
        val resSinOrigen = LinkProtocol.validateUsername(testUser, origen = null)
        assertFalse("Test0001 sin origen=prueba debe fallar", resSinOrigen.isValid)
        assertEquals(LinkCodes.INVALID_USERNAME, resSinOrigen.errorCode)

        val resOrigenRegistro = LinkProtocol.validateUsername(testUser, origen = "registro")
        assertFalse("Test0001 con origen=registro debe fallar", resOrigenRegistro.isValid)

        // Con origen=prueba -> debe pasar
        val resConPrueba = LinkProtocol.validateUsername(testUser, origen = "prueba")
        assertTrue("Test0001 con origen=prueba debe ser válido", resConPrueba.isValid)

        // Caso test minúscula con números
        val resMinusculaSinPrueba = LinkProtocol.validateUsername("test9999", origen = "registro")
        assertFalse("test9999 sin origen=prueba debe fallar", resMinusculaSinPrueba.isValid)

        // Usuario estándar válido
        val normalUser = "juan_perez.23"
        assertTrue(LinkProtocol.validateUsername(normalUser, origen = "registro").isValid)

        // Longitudes inválidas (< 4 o > 32)
        assertFalse("Menos de 4 chars debe fallar", LinkProtocol.validateUsername("abc").isValid)
        assertFalse("Más de 32 chars debe fallar", LinkProtocol.validateUsername("a".repeat(33)).isValid)

        // Caracteres no permitidos
        assertFalse("Arroba no permitida", LinkProtocol.validateUsername("user@sdmx").isValid)
        assertFalse("Espacios no permitidos", LinkProtocol.validateUsername("user sdmx").isValid)
    }

    @Test
    fun testValidateDateEdgeCases() {
        val today = "2026-10-26"

        // Fecha pasada -> debe fallar
        val fechaPasada = "2026-10-25"
        val resPasada = LinkProtocol.validateDate(fechaPasada, todayDateString = today)
        assertFalse("Fecha pasada debe fallar", resPasada.isValid)
        assertEquals(LinkCodes.INVALID_DATE, resPasada.errorCode)

        // Mismo día de hoy -> debe ser válido (>= hoy)
        val resHoy = LinkProtocol.validateDate(today, todayDateString = today)
        assertTrue("El mismo día del vencimiento debe ser válido", resHoy.isValid)

        // Fecha futura razonable (ej. 30 días después)
        val resFutura = LinkProtocol.validateDate("2026-11-25", todayDateString = today)
        assertTrue("Fecha futura dentro de 400 días debe ser válida", resFutura.isValid)

        // Límite 400 días (ej. +450 días) -> debe fallar
        val fechaMuyFutura = "2028-01-30" // > 400 días desde 2026-10-26
        val resMuyFutura = LinkProtocol.validateDate(fechaMuyFutura, todayDateString = today)
        assertFalse("Fecha mayor a 400 días debe fallar", resMuyFutura.isValid)

        // 29 de febrero:
        // Año NO bisiesto 2026: 2026-02-29 NO existe -> debe fallar
        val res29FebNoBisiesto = LinkProtocol.validateDate("2026-02-29", todayDateString = "2026-01-01")
        assertFalse("29 de febrero en 2026 (no bisiesto) debe fallar", res29FebNoBisiesto.isValid)
        assertEquals(LinkCodes.INVALID_DATE, res29FebNoBisiesto.errorCode)

        // Año bisiesto 2028: 2028-02-29 SI existe y dentro de 400 días desde 2027-10-01
        val res29FebBisiesto = LinkProtocol.validateDate("2028-02-29", todayDateString = "2027-10-01")
        assertTrue("29 de febrero en 2028 (año bisiesto) debe ser válida", res29FebBisiesto.isValid)
    }

    // -------------------------------------------------------------
    // 4. IDEMPOTENCIA (ProcessedStore)
    // -------------------------------------------------------------

    @Test
    fun testProcessedStoreIdempotencyAndPersistence() = runBlocking {
        val testDir = tempFolder.newFolder("processed_test")
        val store = ProcessedStore(testDir, "test_processed.json")

        val cmdId = "uuid-cmd-999"
        val response = LinkProtocol.buildResponse(
            re = cmdId,
            ok = true,
            code = LinkCodes.OK,
            msg = "Operación procesada",
            data = "exito",
            secret = testSecret
        )

        // Inicialmente no está
        assertFalse(store.has(cmdId))
        assertNull(store.get(cmdId))

        // Almacenar respuesta
        store.put(cmdId, response)

        // Ahora está presente
        assertTrue(store.has(cmdId))
        val retrieved = store.get(cmdId)
        assertNotNull(retrieved)
        assertEquals(cmdId, retrieved?.re)
        assertEquals(response.sig, retrieved?.sig)

        // Probar persistencia reabriendo el store desde el mismo archivo
        val newStoreInstance = ProcessedStore(testDir, "test_processed.json")
        assertTrue("La nueva instancia debe cargar el comando persistido", newStoreInstance.has(cmdId))
        val reloaded = newStoreInstance.get(cmdId)
        assertEquals(response.sig, reloaded?.sig)
    }

    @Test
    fun testProcessedStoreCapacityLimit1000() = runBlocking {
        val testDir = tempFolder.newFolder("capacity_test")
        val store = ProcessedStore(testDir, "capacity.json")

        // Insertar 1005 items
        for (i in 1..1005) {
            val id = "cmd-$i"
            val resp = LinkEnvelope(
                v = 1,
                id = "resp-$i",
                ts = 1767000000L + i,
                re = id,
                payload = """{"ok":true,"code":"OK","msg":"$i"}""",
                sig = "dummy"
            )
            store.put(id, resp)
        }

        // El tamaño no debe superar 1000
        assertEquals(1000, store.size())

        // Los primeros 5 (cmd-1 a cmd-5) deben haber sido purgados
        for (i in 1..5) {
            assertFalse("cmd-$i debería haber sido purgado por FIFO", store.has("cmd-$i"))
        }

        // Los últimos (ej. cmd-1005) deben estar presentes
        assertTrue(store.has("cmd-1005"))
        assertTrue(store.has("cmd-6"))
    }

    // -------------------------------------------------------------
    // 5. RATE LIMITER (30 comandos/min)
    // -------------------------------------------------------------

    @Test
    fun testRateLimiter30CommandsPerMinute() {
        val rateLimiter = RateLimiter(maxPerMinute = 30)
        val baseTime = 1000000L

        // Los primeros 30 en el mismo minuto deben permitirse
        for (i in 1..30) {
            assertTrue("Comando #$i debe permitirse", rateLimiter.tryAcquire(nowMs = baseTime + (i * 100)))
        }

        // El comando #31 dentro del mismo minuto debe rechazarse con false (RATE_LIMITED)
        assertFalse("El comando #31 dentro de la ventana de 60s debe ser rechazado", rateLimiter.tryAcquire(nowMs = baseTime + 4000))

        // Al avanzar 61 segundos (fuera de la ventana de los primeros), debe volver a permitir
        val futureTime = baseTime + 65000L
        assertTrue("Al salir de la ventana de 60s debe permitir nuevos comandos", rateLimiter.tryAcquire(nowMs = futureTime))
    }
}
