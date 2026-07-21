package com.example.data

import com.google.gson.annotations.SerializedName

class UserModel(
    id: String? = "",
    usuario: String? = "",
    password: String? = "",
    vencimiento: String? = "",
    adultos: Boolean? = false
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

    var id: String
        get() = _id ?: ""
        set(value) { _id = value }

    val usuario: String
        get() = _usuario ?: ""

    val password: String
        get() = _password ?: ""

    val vencimiento: String
        get() = _vencimiento ?: ""

    val adultos: Boolean
        get() = _adultos ?: false

    fun copy(
        id: String = this.id,
        usuario: String = this.usuario,
        password: String = this.password,
        vencimiento: String = this.vencimiento,
        adultos: Boolean = this.adultos
    ): UserModel {
        return UserModel(id, usuario, password, vencimiento, adultos)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserModel) return false
        return id == other.id &&
               usuario == other.usuario &&
               password == other.password &&
               vencimiento == other.vencimiento &&
               adultos == other.adultos
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + usuario.hashCode()
        result = 31 * result + password.hashCode()
        result = 31 * result + vencimiento.hashCode()
        result = 31 * result + adultos.hashCode()
        return result
    }
}
