package com.manhwaviewer.ios.eval

import com.manhwaviewer.data.preference.AttestationStateStore
import com.manhwaviewer.data.preference.createDataStore
import com.manhwaviewer.data.translation.attestation.AppAttestAttestor
import com.manhwaviewer.data.translation.attestation.AttestationProof
import com.manhwaviewer.data.translation.attestation.AttestationResult
import kotlin.time.TimeSource
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.DeviceCheck.DCAppAttestService
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

/**
 * Exercises [AppAttestAttestor] exactly as the app wires it, so a real App Attest round trip can be
 * timed and its proof inspected without shipping any of this in the app itself.
 *
 * Called from the bench app target, never from the app itself. The DataStore file lives inside the
 * same eval directory the controller writes to, so a key id generated on one launch survives into
 * the next phase (attest once, then assert with the credential the proxy hands back).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
object AttestationEvalHarness {

    private fun readText(path: String): String {
        val data = NSData.dataWithContentsOfFile(path) ?: error("cannot read $path")
        return ByteArray(data.length.toInt()).also { bytes ->
            bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
        }.decodeToString()
    }

    private fun write(path: String, text: String) {
        NSString.create(string = text).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    private fun proofJson(proof: AttestationProof): JsonObject = buildJsonObject {
        put("platform", JsonPrimitive(proof.platform))
        proof.keyId?.let { put("keyId", JsonPrimitive(it)) }
        proof.attestation?.let { put("attestation", JsonPrimitive(it)) }
        proof.assertion?.let { put("assertion", JsonPrimitive(it)) }
        proof.credential?.let { put("credential", JsonPrimitive(it)) }
    }

    /** Blocks until `prove()` returns. Returns a one-line status; the full result lands in `out.json`. */
    fun run(dir: String): String {
        val outPath = "$dir/out.json"
        NSFileManager.defaultManager.removeItemAtPath(outPath, error = null)
        return try {
            val input = Json.parseToJsonElement(readText("$dir/in.json")).jsonObject
            val challenge = input.getValue("challenge").jsonPrimitive.content
            val deviceId = input.getValue("deviceId").jsonPrimitive.content
            val credential = input["credential"]?.jsonPrimitive?.contentOrNull

            val store = AttestationStateStore(createDataStore { "$dir/state.preferences_pb" })
            val attestor = AppAttestAttestor(store)

            val resultName = runBlocking {
                if (credential != null) attestor.onCredential(credential, null)

                val mark = TimeSource.Monotonic.markNow()
                val result = attestor.prove(challenge, deviceId)
                val elapsedMs = mark.elapsedNow().inWholeMilliseconds

                val outcome = buildJsonObject {
                    put("supported", JsonPrimitive(DCAppAttestService.sharedService.supported))
                    put("elapsedMs", JsonPrimitive(elapsedMs))
                    when (result) {
                        is AttestationResult.Proof -> {
                            put("result", JsonPrimitive("proof"))
                            put("proof", proofJson(result.proof))
                        }
                        AttestationResult.Unavailable -> put("result", JsonPrimitive("unavailable"))
                        AttestationResult.TransientFailure -> put("result", JsonPrimitive("transient"))
                    }
                }
                write(outPath, outcome.toString())
                outcome["result"].toString()
            }
            "done: $resultName"
        } catch (e: Throwable) {
            write(outPath, buildJsonObject { put("result", JsonPrimitive("error")); put("error", JsonPrimitive(e::class.simpleName ?: "Unknown")) }.toString())
            "failed: ${e::class.simpleName}"
        }
    }
}
