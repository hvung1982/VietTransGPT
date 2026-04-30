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

    companion object {
        private const val KEY_PROMPT = "default_prompt"
    }
}
