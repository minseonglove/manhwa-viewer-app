package com.manhwaviewer.domain.repository

import com.manhwaviewer.domain.entity.ViewerPreference
import kotlinx.coroutines.flow.Flow

interface ViewerPreferenceRepository {
    fun getPreference(): Flow<ViewerPreference>
    suspend fun updatePreference(preference: ViewerPreference)
}
