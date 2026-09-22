package com.manhwaviewer.data.translation.attestation

import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals

class IntegrityErrorsTest {

    @Test
    fun devicesWithoutPlayCannotAttest() {
        for (code in listOf(
            StandardIntegrityErrorCode.API_NOT_AVAILABLE,
            StandardIntegrityErrorCode.PLAY_STORE_NOT_FOUND,
            StandardIntegrityErrorCode.PLAY_SERVICES_NOT_FOUND,
            StandardIntegrityErrorCode.CANNOT_BIND_TO_SERVICE,
            StandardIntegrityErrorCode.PLAY_STORE_VERSION_OUTDATED,
            StandardIntegrityErrorCode.PLAY_SERVICES_VERSION_OUTDATED,
            StandardIntegrityErrorCode.CLOUD_PROJECT_NUMBER_IS_INVALID,
        )) {
            assertEquals(AttestationResult.Unavailable, classifyIntegrityError(code), "code $code")
        }
    }

    @Test
    fun everythingElseIsWorthRetryingLater() {
        for (code in listOf(
            StandardIntegrityErrorCode.NETWORK_ERROR,
            StandardIntegrityErrorCode.TOO_MANY_REQUESTS,
            StandardIntegrityErrorCode.GOOGLE_SERVER_UNAVAILABLE,
            StandardIntegrityErrorCode.CLIENT_TRANSIENT_ERROR,
            StandardIntegrityErrorCode.INTERNAL_ERROR,
        )) {
            assertEquals(AttestationResult.TransientFailure, classifyIntegrityError(code), "code $code")
        }
    }
}
