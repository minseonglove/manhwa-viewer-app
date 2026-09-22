package com.manhwaviewer.data.ocr.eval.litert

import android.os.Bundle
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel

/**
 * How a LiteRT model is compiled for a measurement run.
 *
 * Read from instrumentation arguments so the same APK can compare settings on the same device:
 * `-e litertAccel gpu|gpu+cpu|cpu`, `-e litertPrecision fp16|fp32`, `-e litertBackend auto|opencl|opengl`,
 * `-e litertStorage default|buffer|texture`, `-e litertThreads <n>` (CPU side),
 * `-e litertPriority default|low|normal|high`, `-e litertCmdPrep <n>` (command buffer preparation steps),
 * `-e litertTexWeights true|false`.
 */
data class LiteRtConfig(
    /**
     * GPU alone, or GPU with CPU allowed to take the ops the GPU cannot. GPU alone fails to compile
     * any graph the GPU does not cover completely, which on this device is all of them.
     */
    val accelerators: Set<Accelerator> = setOf(Accelerator.GPU, Accelerator.CPU),
    val precision: CompiledModel.GpuOptions.Precision = CompiledModel.GpuOptions.Precision.FP16,
    val backend: CompiledModel.GpuOptions.Backend = CompiledModel.GpuOptions.Backend.AUTOMATIC,
    val storage: CompiledModel.GpuOptions.BufferStorageType = CompiledModel.GpuOptions.BufferStorageType.DEFAULT,
    /** Four, as ORT uses in the app, so the CPU side is not compared at a different thread count. */
    val cpuThreads: Int = 4,
    val priority: CompiledModel.GpuOptions.Priority? = null,
    val commandBufferPreparations: Int? = null,
    val preferTextureWeights: Boolean? = null,
) {

    /** A fresh object per model: [CompiledModel.Options] is mutable. */
    fun options(): CompiledModel.Options = CompiledModel.Options(accelerators).apply {
        if (Accelerator.GPU in accelerators) {
            gpuOptions = CompiledModel.GpuOptions(
                precision = precision,
                bufferStorageType = storage,
                preferTextureWeights = preferTextureWeights,
                backend = backend,
                priority = priority,
                numStepsOfCommandBufferPreparations = commandBufferPreparations,
            )
        }
        if (Accelerator.CPU in accelerators) {
            cpuOptions = CompiledModel.CpuOptions(numThreads = cpuThreads)
        }
    }

    val label: String
        get() {
            val accel = accelerators.sortedBy { it.ordinal }.reversed().joinToString("+") { it.name.lowercase() }
            return if (Accelerator.GPU in accelerators) {
                "litert-$accel-${precision.name.lowercase()}-${backend.name.lowercase()}-${storage.name.lowercase()}" +
                    (priority?.let { "-p${it.name.lowercase()}" } ?: "") +
                    (commandBufferPreparations?.let { "-cb$it" } ?: "") +
                    (preferTextureWeights?.let { "-tw$it" } ?: "")
            } else {
                "litert-$accel-t$cpuThreads"
            }
        }

    companion object {
        fun fromArguments(args: Bundle): LiteRtConfig = LiteRtConfig(
            accelerators = when (args.getString("litertAccel") ?: "gpu+cpu") {
                "gpu" -> setOf(Accelerator.GPU)
                "gpu+cpu" -> setOf(Accelerator.GPU, Accelerator.CPU)
                "cpu" -> setOf(Accelerator.CPU)
                else -> error("litertAccel is gpu, gpu+cpu or cpu")
            },
            precision = when (args.getString("litertPrecision") ?: "fp16") {
                "fp16" -> CompiledModel.GpuOptions.Precision.FP16
                "fp32" -> CompiledModel.GpuOptions.Precision.FP32
                else -> error("litertPrecision is fp16 or fp32")
            },
            backend = when (args.getString("litertBackend") ?: "auto") {
                "auto" -> CompiledModel.GpuOptions.Backend.AUTOMATIC
                "opencl" -> CompiledModel.GpuOptions.Backend.OPENCL
                "opengl" -> CompiledModel.GpuOptions.Backend.OPENGL
                else -> error("litertBackend is auto, opencl or opengl")
            },
            storage = when (args.getString("litertStorage") ?: "default") {
                "default" -> CompiledModel.GpuOptions.BufferStorageType.DEFAULT
                "buffer" -> CompiledModel.GpuOptions.BufferStorageType.BUFFER
                "texture" -> CompiledModel.GpuOptions.BufferStorageType.TEXTURE_2D
                else -> error("litertStorage is default, buffer or texture")
            },
            cpuThreads = args.getString("litertThreads")?.toInt() ?: 4,
            priority = args.getString("litertPriority")?.let { CompiledModel.GpuOptions.Priority.valueOf(it.uppercase()) },
            commandBufferPreparations = args.getString("litertCmdPrep")?.toInt(),
            preferTextureWeights = args.getString("litertTexWeights")?.toBooleanStrict(),
        )

        /**
         * `-e decoderAccel same|cpu|gpu+cpu`: where prefill and decode run. `same` follows the encoder;
         * the author's int8 decoder graphs need `cpu`, since their GPU compile fails or crashes.
         */
        fun decoderFromArguments(args: Bundle): LiteRtConfig {
            val base = fromArguments(args)
            return when (args.getString("decoderAccel") ?: "same") {
                "same" -> base
                "cpu" -> base.copy(accelerators = setOf(Accelerator.CPU))
                "gpu+cpu" -> base.copy(accelerators = setOf(Accelerator.GPU, Accelerator.CPU))
                else -> error("decoderAccel is same, cpu or gpu+cpu")
            }
        }
    }
}
