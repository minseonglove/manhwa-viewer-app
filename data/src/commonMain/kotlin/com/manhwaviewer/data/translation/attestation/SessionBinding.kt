package com.manhwaviewer.data.translation.attestation

import okio.Buffer
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** What the device signs or has the platform vouch for; it must match the proxy's computeBinding byte for byte. */
@OptIn(ExperimentalEncodingApi::class)
object SessionBinding {

    private val urlSafe = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    fun compute(challenge: String, deviceId: String, key: String): ByteArray =
        Buffer()
            .writeUtf8("mv-session-v1").writeByte(0)
            .writeUtf8(challenge).writeByte(0)
            .writeUtf8(deviceId).writeByte(0)
            .writeUtf8(key)
            .sha256()
            .toByteArray()

    fun base64Url(bytes: ByteArray): String = urlSafe.encode(bytes)
}
