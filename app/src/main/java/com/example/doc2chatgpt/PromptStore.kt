package com.example.doc2chatgpt

import android.content.Context
import androidx.core.content.edit

class PromptStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("prompt_store", Context.MODE_PRIVATE)

    fun getPrompt(defaultPrompt: String): String {
        return preferences.getString(KEY_PROMPT, defaultPrompt) ?: defaultPrompt
    }

    fun savePrompt(prompt: String) {
        preferences.edit { putString(KEY_PROMPT, prompt) }
    }

    fun getPromptSlot(slot: Int, fallback: String): String {
        val key = promptSlotKey(slot)
        return preferences.getString(key, fallback) ?: fallback
    }

    fun savePromptSlot(slot: Int, prompt: String) {
        preferences.edit { putString(promptSlotKey(slot), prompt) }
    }

    private fun promptSlotKey(slot: Int): String = "prompt_slot_$slot"

    companion object {
        private const val KEY_PROMPT = "default_prompt"
    }
}
