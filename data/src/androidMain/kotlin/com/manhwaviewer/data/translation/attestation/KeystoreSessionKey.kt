package com.manhwaviewer.data.translation.attestation

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.ProviderException
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * The install's EC P-256 signing key. Keystore binds hardware keys to the device's verified boot state, so after a bootloader
 * unlock or a different system image this key can no longer sign and the install has to register again.
 */
internal class KeystoreSessionKey private constructor(
    private val privateKey: PrivateKey,
    override val publicKeySpki: ByteArray,
    override val hardwareBacked: Boolean,
) : SessionKeyLike {

    override fun sign(data: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(data)
            sign()
        }

    companion object {
        private const val ALIAS = "manhwaviewer.translation.session"
        private const val PROVIDER = "AndroidKeyStore"

        fun getOrCreate(): KeystoreSessionKey {
            val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
            val entry = keyStore.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry ?: run {
                generate()
                keyStore.getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry
            }
            return KeystoreSessionKey(entry.privateKey, entry.certificate.publicKey.encoded, isHardwareBacked(entry.privateKey))
        }

        /** Removes the alias so the next [getOrCreate] generates a fresh key. Recovery must proceed even if this fails, so it swallows everything. */
        fun delete() {
            try {
                KeyStore.getInstance(PROVIDER).apply { load(null) }.deleteEntry(ALIAS)
            } catch (e: Exception) {
                // The alias may already be gone, or the store itself unusable; either way there is nothing more to clean up.
            }
        }

        private fun generate() {
            fun spec(strongBox: Boolean) = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .apply { if (strongBox) setIsStrongBoxBacked(true) }
                .build()
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    generator.initialize(spec(strongBox = true))
                    generator.generateKeyPair()
                    return
                } catch (e: ProviderException) {
                    // StrongBoxUnavailableException extends ProviderException; fall through to the TEE.
                }
            }
            generator.initialize(spec(strongBox = false))
            generator.generateKeyPair()
        }

        private fun isHardwareBacked(key: PrivateKey): Boolean {
            val info = KeyFactory.getInstance(key.algorithm, PROVIDER).getKeySpec(key, KeyInfo::class.java)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                info.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ||
                    info.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
            } else {
                // securityLevel exists only from API 31; below that (minSdk 26) this is the only way to ask.
                @Suppress("DEPRECATION")
                info.isInsideSecureHardware
            }
        }
    }
}
