package com.manhwaviewer.data.preference

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.random.Random

/**
 * A random identifier made on first use and kept for the life of the install.
 *
 * The translation proxy counts usage per device with it. It identifies nothing but this install,
 * and clearing the app's data starts a new one.
 */
class InstallIdStore(private val dataStore: DataStore<Preferences>) {

    private val key = stringPreferencesKey("install_id")

    suspend fun get(): String =
        dataStore.edit { prefs ->
            if (prefs[key] == null) prefs[key] = newId()
        }[key]!!

    private fun newId(): String = Random.nextBytes(16).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
