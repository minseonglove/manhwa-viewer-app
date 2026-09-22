package com.manhwaviewer.data.ocr.inference

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

/**
 * The one thread every LiteRT model in the app is compiled and run on.
 *
 * LiteRT's OpenGL backend is bound to the thread that created its context, and the GPU executes one
 * job at a time regardless, so callers on any thread are funnelled here. A call made from this thread
 * runs inline rather than queueing behind itself.
 */
object LiteRtThread {

    @Volatile
    private var thread: Thread? = null
    private var environment: Environment? = null

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "litert").apply { isDaemon = true }.also { thread = it }
    }

    fun <T> call(block: () -> T): T {
        if (Thread.currentThread() === thread) return block()
        return try {
            executor.submit(Callable(block)).get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    /** The shared environment; only valid on this thread, which [call] guarantees. */
    fun environment(): Environment {
        check(Thread.currentThread() === thread) { "LiteRT is driven from its own thread; use LiteRtThread.call" }
        return environment ?: Environment.create().also { environment = it }
    }
}

/** Compiles app models from the APK's assets, on the GPU when it proves itself, else on the CPU. */
object LiteRtModels {

    /** Four, as ORT uses in this app, measured best on a 10-core big.LITTLE device. */
    private const val CPU_THREADS = 4

    fun open(
        context: Context,
        assetPath: String,
        cacheName: String,
        preferGpu: Boolean,
        agrees: (CompiledModel) -> Boolean,
    ): AcceleratorFallback.Opened<CompiledModel> = LiteRtThread.call {
        val history = FileGpuHistory(
            FileGpuHistory.directory(File(context.cacheDir, "litert-gpu"), cacheName, buildStamp(context)),
            Process.myPid(),
        ) { pid -> diedOfCrash(context, pid) }
        val opened = AcceleratorFallback.open(
            preferGpu = preferGpu,
            gpu = { CompiledModel.create(context.assets, assetPath, gpuOptions(history.dir, cacheName), LiteRtThread.environment()) },
            cpu = { compileOnCpu(context, assetPath) },
            agrees = agrees,
            release = { it.close() },
            history = history,
        )
        println("OCR | $assetPath on ${opened.accelerator}" + (opened.gpuFailure?.let { " (GPU rejected: $it)" } ?: ""))
        opened
    }

    fun compileOnCpu(context: Context, assetPath: String): CompiledModel =
        CompiledModel.create(context.assets, assetPath, cpuOptions(), LiteRtThread.environment())

    /** Changes with every install or update of the app, and with every OS update, which is when GPU drivers change. */
    private fun buildStamp(context: Context): String {
        val apk = File(context.applicationInfo.sourceDir).lastModified()
        return "$apk-${Build.FINGERPRINT.hashCode().toUInt().toString(16)}"
    }

    /** Unknowable before Android 11, and then counted as a crash so a crashing driver is still caught. */
    private fun diedOfCrash(context: Context, pid: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        val exit = context.getSystemService(ActivityManager::class.java)
            ?.getHistoricalProcessExitReasons(context.packageName, pid, 1)
            ?.firstOrNull()
            ?: return true
        val notCrashes = setOf(
            ApplicationExitInfo.REASON_EXIT_SELF,
            ApplicationExitInfo.REASON_LOW_MEMORY,
            ApplicationExitInfo.REASON_USER_REQUESTED,
            ApplicationExitInfo.REASON_USER_STOPPED,
        )
        return exit.reason !in notCrashes
    }

    private fun cpuOptions() = CompiledModel.Options(Accelerator.CPU).apply {
        cpuOptions = CompiledModel.CpuOptions(numThreads = CPU_THREADS)
    }

    /**
     * GPU with the CPU allowed: with the GPU alone, any graph the delegate does not cover completely
     * fails to compile (YOLO's top-k tail stays on the CPU). fp16 compute measured no loss in
     * recognition quality. The program cache keeps the OpenCL kernels between launches, which
     * otherwise cost several seconds of compile on every first use.
     */
    private fun gpuOptions(cacheDir: File, cacheName: String) = CompiledModel.Options(Accelerator.GPU, Accelerator.CPU).apply {
        gpuOptions = CompiledModel.GpuOptions(
            precision = CompiledModel.GpuOptions.Precision.FP16,
            serializationDir = cacheDir.absolutePath,
            modelCacheKey = cacheName,
            serializeProgramCache = true,
        )
        cpuOptions = CompiledModel.CpuOptions(numThreads = CPU_THREADS)
    }
}
