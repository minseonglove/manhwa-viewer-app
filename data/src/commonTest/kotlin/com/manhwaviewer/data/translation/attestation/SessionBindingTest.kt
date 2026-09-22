package com.manhwaviewer.data.translation.attestation

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionBindingTest {

    private fun ByteArray.hex() = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    // The proxy's challenge.test.ts asserts the same vectors; both sides must agree byte for byte.
    @Test
    fun matchesTheProxyVectors() {
        val vectors = listOf(
            Triple("c", "d", "k") to ("8eab13fe9169245e0c3e752e78e10d461f41f94b2383e530f6895d0d304ddcca" to "jqsT_pFpJF4MPnUueOENRh9B-Usjg-Uw9oldDTBN3Mo"),
            Triple("chal.sig", "0123456789abcdef0123456789abcdef", "zgSY9YSD+7TaDXssY6WlOPVS1K3Lmk+pFhlcSWE+ZV0=") to
                ("6b2e56dd3461328aeaaaf832401ce623386c2158c6efc13690a2a2dc01f277c1" to "ay5W3TRhMorqqvgyQBzmIzhsIVjG78E2kKKi3AHyd8E"),
            Triple("한", "d", "") to ("b989cd042b90d56b682cd02c2cca9705a93ce75bdec0423e4a5922282a3bc551" to "uYnNBCuQ1WtoLNAsLMqXBak851vewEI-SlkiKCo7xVE"),
        )
        for ((input, expected) in vectors) {
            val binding = SessionBinding.compute(input.first, input.second, input.third)
            assertEquals(expected.first, binding.hex())
            assertEquals(expected.second, SessionBinding.base64Url(binding))
        }
    }
}
