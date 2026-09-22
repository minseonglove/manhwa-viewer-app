package com.manhwaviewer.data.preference

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

data class AttestationState(val keyId: String?, val credential: String?, val recheckAtEpochSeconds: Long)

/** The device key id and the proxy's credential for it. Clearing the app's data starts attestation over. */
class AttestationStateStore(private val dataStore: DataStore<Preferences>) {

    private val keyIdKey = stringPreferencesKey("attestation_key_id")
    private val credentialKey = stringPreferencesKey("attestation_credential")
    private val recheckAtKey = longPreferencesKey("attestation_recheck_at")

    suspend fun read(): AttestationState {
        val prefs = dataStore.data.first()
        return AttestationState(prefs[keyIdKey], prefs[credentialKey], prefs[recheckAtKey] ?: 0)
    }

    suspend fun saveKeyId(keyId: String) {
        dataStore.edit { it[keyIdKey] = keyId }
    }

    suspend fun saveCredential(credential: String, recheckAtEpochSeconds: Long) {
        dataStore.edit {
            it[credentialKey] = credential
            it[recheckAtKey] = recheckAtEpochSeconds
        }
    }

    suspend fun clearCredential() {
        dataStore.edit {
            it.remove(credentialKey)
            it.remove(recheckAtKey)
        }
    }

    suspend fun clearAll() {
        dataStore.edit {
            it.remove(keyIdKey)
            it.remove(credentialKey)
            it.remove(recheckAtKey)
        }
    }
}
