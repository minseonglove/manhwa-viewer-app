package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.entity.ViewerPreference
import com.manhwaviewer.domain.repository.ViewerPreferenceRepository

class UpdateViewerPreferenceUseCase(
    private val repository: ViewerPreferenceRepository,
) {
    suspend operator fun invoke(preference: ViewerPreference) {
        repository.updatePreference(preference)
    }
}
