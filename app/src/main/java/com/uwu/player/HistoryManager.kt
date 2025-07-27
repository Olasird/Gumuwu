package com.uwu.player

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object HistoryManager {

    private const val PREFS_NAME = "HistoryPrefs"
    private const val HISTORY_KEY = "view_history"
    private const val SESSION_KEY = "current_session" // Clave para el marcador temporal
    private const val VIEWING_THRESHOLD_MS = 20000L // 2 minutos

    // Guarda el inicio de una sesión de visionado
    fun startWatching(context: Context, contentId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sessionData = mapOf("contentId" to contentId, "startTime" to System.currentTimeMillis())
        val json = Gson().toJson(sessionData)
        prefs.edit().putString(SESSION_KEY, json).apply()
    }

    // Procesa el final de una sesión y la guarda si cumple el tiempo
    fun stopWatchingAndSave(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sessionJson = prefs.getString(SESSION_KEY, null)

        if (sessionJson != null) {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val sessionData: Map<String, Any> = Gson().fromJson(sessionJson, type)
            val contentId = sessionData["contentId"] as? String
            // Firebase guarda los números como Double, así que lo leemos como Double
            val startTime = (sessionData["startTime"] as? Double)?.toLong()

            if (contentId != null && startTime != null) {
                val watchTime = System.currentTimeMillis() - startTime
                if (watchTime >= VIEWING_THRESHOLD_MS) {
                    addContentToHistory(context, contentId)
                }
            }
            // Limpiamos el marcador de sesión
            prefs.edit().remove(SESSION_KEY).apply()
        }
    }

    // Función que se llama al iniciar la app para procesar sesiones interrumpidas
    fun checkPendingHistory(context: Context) {
        // La lógica es la misma que al parar de ver, así que reutilizamos la función
        stopWatchingAndSave(context)
    }

    // Añade un ID al principio de la lista de historial
    fun addContentToHistory(context: Context, contentId: String) {
        val history = getHistory(context).toMutableList()
        history.remove(contentId) // Lo quita si ya estaba para moverlo al frente
        history.add(0, contentId) // Lo añade al principio
        val finalHistory = history.take(10) // Nos aseguramos de que no haya más de 10
        saveHistory(context, finalHistory)
    }

    fun getHistory(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(HISTORY_KEY, null)
        return if (json != null) {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(json, type)
        } else {
            emptyList()
        }
    }

    private fun saveHistory(context: Context, history: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(history)
        prefs.edit().putString(HISTORY_KEY, json).apply()
    }
}