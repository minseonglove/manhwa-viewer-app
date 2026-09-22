package com.manhwaviewer.data.preference

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AttestationStateStoreTest {

    private class InMemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    @Test
    fun keepsKeyAndCredentialAndClearsThemSeparately() = runTest {
        val store = AttestationStateStore(InMemoryDataStore())
        assertEquals(AttestationState(null, null, 0), store.read())

        store.saveKeyId("key-1")
        store.saveCredential("cred", 500)
        assertEquals(AttestationState("key-1", "cred", 500), store.read())

        store.clearCredential()
        assertEquals(AttestationState("key-1", null, 0), store.read())

        store.saveCredential("cred2", 9)
        store.clearAll()
        assertEquals(AttestationState(null, null, 0), store.read())
    }
}
