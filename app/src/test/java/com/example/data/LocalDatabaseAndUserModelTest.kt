package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalDatabaseAndUserModelTest {

    private lateinit var context: Context
    private val gson = Gson()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        LocalDatabase.resetForTesting()
        val file = File(context.filesDir, "sdmx_users.json")
        val tmpFile = File(context.filesDir, "sdmx_users.json.tmp")
        if (file.exists()) file.delete()
        if (tmpFile.exists()) tmpFile.delete()
    }

    @Test
    fun testLegacyJsonCompatibility() {
        val legacyJson = """
            [
              {
                "id": "1001",
                "usuario": "cliente_antiguo",
                "password": "Password123!",
                "vencimiento": "2026-12-31",
                "adultos": false
              }
            ]
        """.trimIndent()

        val listType = object : TypeToken<List<UserModel>>() {}.type
        val parsed: List<UserModel> = gson.fromJson(legacyJson, listType)

        assertEquals(1, parsed.size)
        val user = parsed[0]
        assertEquals("1001", user.id)
        assertEquals("cliente_antiguo", user.usuario)
        assertEquals("Password123!", user.password)
        assertEquals("2026-12-31", user.vencimiento)
        assertEquals("2026-12-31", user.vence)
        assertFalse(user.adultos)
        assertEquals("manual", user.origen) // Default when missing from legacy JSON
        assertNull(user.creadoEn)
        assertNull(user.telefono)
        assertNull(user.creado_por)
        assertNull(user.notas)
    }

    @Test
    fun testNewFieldsSerializationAndDeserialization() {
        val nowMs = System.currentTimeMillis()
        val newUser = UserModel(
            id = "2002",
            usuario = "cliente_vip",
            password = "SecurePass#2026",
            vencimiento = "2027-01-15",
            adultos = true,
            origen = "vip",
            telefono = "+521234567890",
            creado_por = "admin_wa",
            notas = "Pago trimestral",
            creadoEn = nowMs
        )

        val json = gson.toJson(listOf(newUser))
        assertTrue(json.contains(""""origen": "vip"""") || json.contains(""""origen":"vip""""))
        assertTrue(json.contains(""""telefono": "+521234567890"""") || json.contains(""""telefono":"+521234567890""""))
        assertTrue(json.contains(""""creado_en": $nowMs""") || json.contains(""""creado_en":$nowMs"""))
        // Confirma que 'vence' es una propiedad auxiliar getter y NO se serializa
        assertFalse(json.contains(""""vence""""))

        val listType = object : TypeToken<List<UserModel>>() {}.type
        val roundtrip: List<UserModel> = gson.fromJson(json, listType)
        assertEquals(1, roundtrip.size)
        val rtUser = roundtrip[0]
        assertEquals("vip", rtUser.origen)
        assertEquals("+521234567890", rtUser.telefono)
        assertEquals("admin_wa", rtUser.creado_por)
        assertEquals("Pago trimestral", rtUser.notas)
        assertEquals(nowMs, rtUser.creadoEn)

        // Test copy, equals, and hashCode
        val copyUser = rtUser.copy(telefono = "+529999999999")
        assertEquals(rtUser.id, copyUser.id)
        assertEquals(rtUser.creadoEn, copyUser.creadoEn)
        assertEquals("+529999999999", copyUser.telefono)
        assertNotEquals(rtUser, copyUser)
        assertEquals(rtUser, rtUser.copy())
        assertEquals(rtUser.hashCode(), rtUser.copy().hashCode())
    }

    @Test
    fun testVigenciaAndRemainingDaysCalculation() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.OCTOBER)
            set(Calendar.DAY_OF_MONTH, 26)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
        }
        val refDate: Date = cal.time

        // Same day: Rule says vencimiento >= hoy is vigente (still renewed on the day of expiry)
        val userToday = UserModel(usuario = "user_today", vencimiento = "2026-10-26")
        assertTrue(userToday.isVigente(refDate))
        assertEquals(0L, userToday.getDiasRestantes(refDate))
        assertEquals("vigente", userToday.getEstado(refDate))

        // Future day
        val userFuture = UserModel(usuario = "user_future", vencimiento = "2026-10-28")
        assertTrue(userFuture.isVigente(refDate))
        assertEquals(2L, userFuture.getDiasRestantes(refDate))
        assertEquals("vigente", userFuture.getEstado(refDate))

        // Past day
        val userPast = UserModel(usuario = "user_past", vencimiento = "2026-10-25")
        assertFalse(userPast.isVigente(refDate))
        assertEquals(-1L, userPast.getDiasRestantes(refDate))
        assertEquals("vencido", userPast.getEstado(refDate))

        // Leap year date test
        val leapDate = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2028)
            set(Calendar.MONTH, Calendar.FEBRUARY)
            set(Calendar.DAY_OF_MONTH, 28)
        }.time
        val leapUser = UserModel(usuario = "leap_user", vencimiento = "2028-02-29")
        assertTrue(leapUser.isVigente(leapDate))
        assertEquals(1L, leapUser.getDiasRestantes(leapDate))
    }

    @Test
    fun testLocalDatabaseSingleton() {
        val db1 = LocalDatabase.get(context)
        val db2 = LocalDatabase.get(context)
        assertSame(db1, db2)
    }

    @Test
    fun testLocalDatabaseAtomicWritesAndPersistence() = runBlocking {
        val db = LocalDatabase.get(context)
        val users = listOf(
            UserModel(id = "1", usuario = "user1", password = "pwd", vencimiento = "2026-11-01"),
            UserModel(id = "2", usuario = "user2", password = "pwd", vencimiento = "2026-11-02")
        )

        db.saveUsers(users)

        // Verify stored in file and matches
        val dbUsers = db.getUsers()
        assertEquals(2, dbUsers.size)
        assertEquals("user1", dbUsers[0].usuario)

        // Verify temp file does not remain lingering
        val tmpFile = db.getTempFile()
        assertFalse(tmpFile.exists())
    }

    @Test
    fun testCorruptJsonPreservesBakAndDoesNotOverwriteWithEmptyList() {
        val file = File(context.filesDir, "sdmx_users.json")
        val bakFile = File(context.filesDir, "sdmx_users.json.bak")
        if (bakFile.exists()) bakFile.delete()

        // Write corrupt JSON
        file.writeText("{ corrupted_invalid_json: true ")

        LocalDatabase.resetForTesting()
        val db = LocalDatabase.get(context)

        // Must preserve .bak copy
        assertTrue(bakFile.exists())
        assertEquals("{ corrupted_invalid_json: true ", bakFile.readText())

        // Must not have wiped the file on disk
        assertTrue(file.exists())
        assertEquals("{ corrupted_invalid_json: true ", file.readText())
    }

    @Test
    fun testLoadDataUsesTmpOnlyIfPrincipalMissingAndValidatesJson() {
        val file = File(context.filesDir, "sdmx_users.json")
        val tmpFile = File(context.filesDir, "sdmx_users.json.tmp")
        if (file.exists()) file.delete()

        // Valid tmp file when principal does not exist
        tmpFile.writeText("""[{"id":"77","usuario":"recovered","password":"p","vencimiento":"2026-12-01"}]""")

        LocalDatabase.resetForTesting()
        val db = LocalDatabase.get(context)

        assertEquals(1, db.users.value.size)
        assertEquals("recovered", db.users.value[0].usuario)
        assertTrue(file.exists()) // recovered to principal
        assertFalse(tmpFile.exists())
    }

    @Test
    fun testDeletedUserNotRecreatedDuringAtomicUpdate() = runBlocking {
        val db = LocalDatabase.get(context)
        val initialUsers = listOf(
            UserModel(id = "10", usuario = "user_keep", password = "pwd", vencimiento = "2026-12-01"),
            UserModel(id = "20", usuario = "user_to_delete", password = "pwd", vencimiento = "2026-12-01")
        )
        db.saveUsers(initialUsers)

        // User deletes user_to_delete mid-cycle
        db.removeUser("user_to_delete")

        // Cycle finishes and applies atomicUpdate with panel IDs
        val panelIds = mapOf("user_keep" to "100", "user_to_delete" to "200")
        db.atomicUpdate { currentUsers ->
            for (i in currentUsers.indices) {
                val u = currentUsers[i]
                val newId = panelIds[u.usuario]
                if (newId != null) {
                    currentUsers[i] = u.copy(id = newId)
                }
            }
        }

        val finalUsers = db.getUsers()
        assertEquals(1, finalUsers.size)
        assertEquals("user_keep", finalUsers[0].usuario)
        assertEquals("100", finalUsers[0].id)
        assertNull(finalUsers.find { it.usuario == "user_to_delete" })
    }

    @Test
    fun testAppLocksDataMutex() = runBlocking {
        assertNotNull(AppLocks.data)
        assertFalse(AppLocks.data.isLocked)
    }

    @Test
    fun testConcurrentAdditionDuringCyclePreserved() = runBlocking {
        val db = LocalDatabase.get(context)
        val initialUsers = listOf(
            UserModel(id = "10", usuario = "original_user", password = "pwd", vencimiento = "2026-12-01")
        )
        db.saveUsers(initialUsers)

        // Simulate new user added via Bot / UI while renewal cycle is running
        val newUserDuringCycle = UserModel(id = "", usuario = "new_incoming_user", password = "pwd", vencimiento = "2026-12-02", origen = "registro")
        db.addUser(newUserDuringCycle)

        // Now renewal cycle finishes and updates table IDs for "original_user"
        val panelTableIds = mapOf("original_user" to "999")
        db.atomicUpdate { currentList ->
            for (i in currentList.indices) {
                val u = currentList[i]
                val newId = panelTableIds[u.usuario]
                if (newId != null) {
                    currentList[i] = u.copy(id = newId)
                }
            }
        }

        // Verify BOTH original_user (with updated id) AND new_incoming_user exist!
        val finalUsers = db.getUsers()
        assertEquals(2, finalUsers.size)
        val orig = finalUsers.find { it.usuario == "original_user" }
        assertNotNull(orig)
        assertEquals("999", orig?.id)

        val incoming = finalUsers.find { it.usuario == "new_incoming_user" }
        assertNotNull(incoming)
        assertEquals("registro", incoming?.origen)
    }

    @Test
    fun testCaseInsensitiveFindAndRemove() = runBlocking {
        val db = LocalDatabase.get(context)
        val user = UserModel(id = "55", usuario = "Chikibaby", password = "pwd", vencimiento = "2026-11-10")
        db.addUser(user)

        val found = db.findUser("chikibaby")
        assertNotNull(found)
        assertEquals("Chikibaby", found?.usuario)

        val removed = db.removeUser("CHIKIBABY")
        assertTrue(removed)
        assertNull(db.findUser("chikibaby"))
    }
}
