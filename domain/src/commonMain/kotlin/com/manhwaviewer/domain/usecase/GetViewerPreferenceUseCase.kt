package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.entity.ViewerPreference
import com.manhwaviewer.domain.repository.ViewerPreferenceRepository
import kotlinx.coroutines.flow.Flow

class GetViewerPreferenceUseCase(
    private val repository: ViewerPreferenceRepository,
) {
    operator fun invoke(): Flow<ViewerPreference> = repository.getPreference()
}
