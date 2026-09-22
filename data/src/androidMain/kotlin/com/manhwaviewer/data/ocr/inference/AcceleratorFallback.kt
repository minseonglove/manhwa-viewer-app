package com.manhwaviewer.data.ocr.inference

enum class OcrAccelerator { GPU, CPU }

/** What earlier launches learned about one model on the GPU. */
interface GpuHistory {
    /** Why the GPU should not be tried this time, or null to try it. */
    fun skipReason(): String?

    /**
     * Brackets a GPU attempt. A GPU driver can kill the process inside compile (a native crash no
     * exception reaches); an attempt that never returns is how the next launch finds out.
     */
    fun attemptStarted()
    fun attemptReturned()

    /** The GPU attempt returned without a model worth keeping. */
    fun rejected()
}

/**
 * Opens a model on the GPU if it compiles there and its output passes [open]'s `agrees` check,
 * otherwise on the CPU.
 *
 * The check is not optional ceremony: LiteRT's GPU delegate compiled the author's Hayai encoder
 * and then computed garbage with it, silently, on both an Exynos (OpenCL) and a Mac (Metal). A
 * GPU model that loads is not yet a GPU model that is right.
 */
object AcceleratorFallback {

    class Opened<T>(val model: T, val accelerator: OcrAccelerator, val gpuFailure: String?)

    fun <T> open(
        preferGpu: Boolean,
        gpu: () -> T,
        cpu: () -> T,
        agrees: (T) -> Boolean,
        release: (T) -> Unit,
        history: GpuHistory? = null,
    ): Opened<T> {
        if (!preferGpu) return Opened(cpu(), OcrAccelerator.CPU, gpuFailure = null)
        history?.skipReason()?.let { return Opened(cpu(), OcrAccelerator.CPU, it) }
        history?.attemptStarted()
        val (model, failure) = try {
            attemptGpu(gpu, agrees, release)
        } finally {
            history?.attemptReturned()
        }
        if (model != null) return Opened(model, OcrAccelerator.GPU, gpuFailure = null)
        history?.rejected()
        return Opened(cpu(), OcrAccelerator.CPU, failure)
    }

    /**
     * The kept model, or null and why it was not kept. [LinkageError] is caught with the exceptions:
     * a native library that is missing or does not match its Java side surfaces as one, and it is
     * as much a reason to use the CPU as a compile error is.
     */
    private fun <T> attemptGpu(gpu: () -> T, agrees: (T) -> Boolean, release: (T) -> Unit): Pair<T?, String?> {
        val model = try {
            gpu()
        } catch (e: Exception) {
            return null to "GPU compile failed: ${e.message ?: e}"
        } catch (e: LinkageError) {
            return null to "GPU compile failed: $e"
        }
        var kept = false
        try {
            val rejection = try {
                if (agrees(model)) null else "GPU output disagrees with the reference"
            } catch (e: Exception) {
                "GPU self-check failed: ${e.message ?: e}"
            } catch (e: LinkageError) {
                "GPU self-check failed: $e"
            }
            kept = rejection == null
            return (if (kept) model else null) to rejection
        } finally {
            if (!kept) release(model)
        }
    }
}
