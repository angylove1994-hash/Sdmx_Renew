package com.example.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class LocalDatabase(private val context: Context) {
    private val file = File(context.filesDir, "sdmx_users.json")
    private val gson = Gson()
    
    private val _users = MutableStateFlow<List<UserModel>>(emptyList())
    val users: StateFlow<List<UserModel>> = _users.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        if (!file.exists()) {
            _users.value = emptyList()
            return
        }
        try {
            val json = file.readText()
            val listType = object : TypeToken<List<UserModel>>() {}.type
            val parsed: List<UserModel>? = gson.fromJson(json, listType)
            _users.value = parsed ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            _users.value = emptyList()
        }
    }

    suspend fun saveUsers(newUsers: List<UserModel>) = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(newUsers)
            file.writeText(json)
            _users.value = newUsers
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun addUser(user: UserModel) {
        val current = _users.value.toMutableList()
        current.add(user)
        saveUsers(current)
    }

    fun getFile(): File = file
}
