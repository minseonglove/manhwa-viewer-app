package com.manhwaviewer.data.preference

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class InstallIdStoreTest {

    private class InMemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    @Test
    fun keepsTheIdItCreatedTheFirstTime() = runTest {
        val dataStore = InMemoryDataStore()

        val first = InstallIdStore(dataStore).get()
        val again = InstallIdStore(dataStore).get()

        assertEquals(first, again)
        assertTrue(Regex("[0-9a-f]{32}").matches(first), first)
    }

    @Test
    fun givesEachInstallItsOwnId() = runTest {
        assertNotEquals(InstallIdStore(InMemoryDataStore()).get(), InstallIdStore(InMemoryDataStore()).get())
    }
}
