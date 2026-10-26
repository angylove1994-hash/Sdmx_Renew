package com.example.data

import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class UserModel(
    id: String? = "",
    usuario: String? = "",
    password: String? = "",
    vencimiento: String? = "",
    adultos: Boolean? = false,
    origen: String? = null,
    telefono: String? = null,
    creado_por: String? = null,
    notas: String? = null,
    creadoEn: Long? = null
) {
    @SerializedName("id")
    private var _id: String? = id

    @SerializedName("usuario")
    private var _usuario: String? = usuario

    @SerializedName("password")
    private var _password: String? = password

    @SerializedName("vencimiento")
    private var _vencimiento: String? = vencimiento

    @SerializedName("adultos")
    private var _adultos: Boolean? = adultos

    @SerializedName("origen")
    private var _origen: String? = origen

    @SerializedName("telefono")
    private var _telefono: String? = telefono

    @SerializedName("creado_por")
    private var _creado_por: String? = creado_por

    @SerializedName("notas")
    private var _notas: String? = notas

    @SerializedName("creado_en")
    private var _creadoEn: Long? = creadoEn

    var id: String
        get() = _id ?: ""
        set(value) { _id = value }

    val usuario: String
        get() = _usuario ?: ""

    val password: String
        get() = _password ?: ""

    val vencimiento: String
        get() = _vencimiento ?: ""

    // Propiedad auxiliar para compatibilidad con el bot/protocolo - getter puro sin campo de respaldo (no se serializa)
    val vence: String
        get() = vencimiento

    val adultos: Boolean
        get() = _adultos ?: false

    var origen: String
        get() = if (_origen.isNullOrBlank()) "manual" else _origen!!
        set(value) { _origen = value }

    var telefono: String?
        get() = _telefono
        set(value) { _telefono = value }

    var creado_por: String?
        get() = _creado_por
        set(value) { _creado_por = value }

    var notas: String?
        get() = _notas
        set(value) { _notas = value }

    var creadoEn: Long?
        get() = _creadoEn
        set(value) { _creadoEn = value }

    fun isVigente(referenceDate: Date = Calendar.getInstance().time): Boolean {
        val clean = vencimiento.trim().substringBefore("T")
        if (clean.isEmpty()) return false
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val fecha = sdf.parse(clean) ?: return false
            val hoyCal = Calendar.getInstance().apply {
                time = referenceDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            !fecha.before(hoyCal.time) // vencimiento >= hoy
        } catch (e: Exception) {
            false
        }
    }

    fun getDiasRestantes(referenceDate: Date = Calendar.getInstance().time): Long {
        val clean = vencimiento.trim().substringBefore("T")
        if (clean.isEmpty()) return -1L
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val fecha = sdf.parse(clean) ?: return -1L
            val hoyCal = Calendar.getInstance().apply {
                time = referenceDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val targetCal = Calendar.getInstance().apply {
                time = fecha
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val diff = targetCal.timeInMillis - hoyCal.timeInMillis
            diff / (1000L * 60 * 60 * 24)
        } catch (e: Exception) {
            -1L
        }
    }

    fun getEstado(referenceDate: Date = Calendar.getInstance().time): String {
        return if (isVigente(referenceDate)) "vigente" else "vencido"
    }

    fun copy(
        id: String = this.id,
        usuario: String = this.usuario,
        password: String = this.password,
        vencimiento: String = this.vencimiento,
        adultos: Boolean = this.adultos,
        origen: String? = this._origen,
        telefono: String? = this.telefono,
        creado_por: String? = this.creado_por,
        notas: String? = this.notas,
        creadoEn: Long? = this.creadoEn
    ): UserModel {
        return UserModel(id, usuario, password, vencimiento, adultos, origen, telefono, creado_por, notas, creadoEn)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserModel) return false
        return id == other.id &&
               usuario == other.usuario &&
               password == other.password &&
               vencimiento == other.vencimiento &&
               adultos == other.adultos &&
               origen == other.origen &&
               telefono == other.telefono &&
               creado_por == other.creado_por &&
               notas == other.notas &&
               creadoEn == other.creadoEn
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + usuario.hashCode()
        result = 31 * result + password.hashCode()
        result = 31 * result + vencimiento.hashCode()
        result = 31 * result + adultos.hashCode()
        result = 31 * result + origen.hashCode()
        result = 31 * result + (telefono?.hashCode() ?: 0)
        result = 31 * result + (creado_por?.hashCode() ?: 0)
        result = 31 * result + (notas?.hashCode() ?: 0)
        result = 31 * result + (creadoEn?.hashCode() ?: 0)
        return result
    }
}
