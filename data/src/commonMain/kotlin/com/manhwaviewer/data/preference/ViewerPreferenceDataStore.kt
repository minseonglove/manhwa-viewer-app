package com.manhwaviewer.data.preference

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.manhwaviewer.domain.entity.BackgroundColor
import com.manhwaviewer.domain.entity.ImageScaleMode
import com.manhwaviewer.domain.entity.ReadingDirection
import com.manhwaviewer.domain.entity.TranslationEngine
import com.manhwaviewer.domain.entity.ViewMode
import com.manhwaviewer.domain.entity.ViewerPreference
import com.manhwaviewer.domain.repository.ViewerPreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ViewerPreferenceDataStore(
    private val dataStore: DataStore<Preferences>,
) : ViewerPreferenceRepository {

    private object Keys {
        val READING_DIRECTION = stringPreferencesKey("reading_direction")
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val KEEP_ZOOM_STATE = booleanPreferencesKey("keep_zoom_state")
        val BACKGROUND_COLOR = stringPreferencesKey("background_color")
        val DEFAULT_TRANSLATION_ENGINE = stringPreferencesKey("default_translation_engine")
        val OFFLINE_TRANSLATION = booleanPreferencesKey("offline_translation")
        val AUTO_TRANSLATE_ON_PAGE_LOAD = booleanPreferencesKey("auto_translate_on_page_load")
        val IMAGE_SCALE_MODE = stringPreferencesKey("image_scale_mode")
    }

    override fun getPreference(): Flow<ViewerPreference> =
        dataStore.data.map { prefs ->
            ViewerPreference(
                readingDirection = prefs[Keys.READING_DIRECTION]
                    ?.let { runCatching { ReadingDirection.valueOf(it) }.getOrNull() }
                    ?: ReadingDirection.LTR,
                viewMode = prefs[Keys.VIEW_MODE]
                    ?.let { runCatching { ViewMode.valueOf(it) }.getOrNull() }
                    ?: ViewMode.SINGLE_PAGE,
                keepZoomState = prefs[Keys.KEEP_ZOOM_STATE] ?: false,
                backgroundColor = prefs[Keys.BACKGROUND_COLOR]
                    ?.let { runCatching { BackgroundColor.valueOf(it) }.getOrNull() }
                    ?: BackgroundColor.BLACK,
                defaultTranslationEngine = prefs[Keys.DEFAULT_TRANSLATION_ENGINE]
                    ?.let { runCatching { TranslationEngine.valueOf(it) }.getOrNull() }
                    ?: TranslationEngine.ML_KIT,
                offlineTranslation = prefs[Keys.OFFLINE_TRANSLATION] ?: true,
                autoTranslateOnPageLoad = prefs[Keys.AUTO_TRANSLATE_ON_PAGE_LOAD] ?: false,
                imageScaleMode = prefs[Keys.IMAGE_SCALE_MODE]
                    ?.let { runCatching { ImageScaleMode.valueOf(it) }.getOrNull() }
                    ?: ImageScaleMode.AUTO_FIT,
            )
        }

    override suspend fun updatePreference(preference: ViewerPreference) {
        dataStore.edit { prefs ->
            prefs[Keys.READING_DIRECTION] = preference.readingDirection.name
            prefs[Keys.VIEW_MODE] = preference.viewMode.name
            prefs[Keys.KEEP_ZOOM_STATE] = preference.keepZoomState
            prefs[Keys.BACKGROUND_COLOR] = preference.backgroundColor.name
            prefs[Keys.DEFAULT_TRANSLATION_ENGINE] = preference.defaultTranslationEngine.name
            prefs[Keys.OFFLINE_TRANSLATION] = preference.offlineTranslation
            prefs[Keys.AUTO_TRANSLATE_ON_PAGE_LOAD] = preference.autoTranslateOnPageLoad
            prefs[Keys.IMAGE_SCALE_MODE] = preference.imageScaleMode.name
        }
    }
}
