package com.manhwaviewer.data.ocr.inference

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtLoggingLevel
import ai.onnxruntime.OrtSession

/**
 * ONNX Runtime 이 세션마다 쥐는 메모리를 다루는 손잡이. 기본값은 모두 ORT 기본값이다.
 *
 * ORT 는 세션마다 BFC 아레나를 만들고 부족할 때마다 영역을 직전의 두 배로 늘려 잡으며, 한 번
 * 늘린 영역은 세션이 닫힐 때까지 돌려주지 않는다. 파일에 내장된 초기화값은 그 아레나에서
 * 나오고, 실행 중 활성값도 그 아레나에서 나온다. 어느 손잡이가 실제로 메모리를 줄이고 속도를
 * 얼마나 내주는지는 기기에서 재야 한다.
 */
data class OrtMemoryOptions(
    /** false 면 BFC 아레나를 끄고 모든 할당을 곧바로 시스템 할당자로 보낸다. */
    val cpuArena: Boolean = true,
    /** false 면 실행 전 활성값 버퍼를 한 덩어리로 미리 잡는 메모리 패턴 최적화를 끈다. */
    val memoryPattern: Boolean = true,
    /** false 면 MatMul 가중치를 커널 전용 배치로 다시 포장하는 prepack 을 끈다. */
    val prepacking: Boolean = true,
    /** true 면 초기화값을 아레나 밖에서 잡아 아레나가 가중치 크기만큼 부풀지 않게 한다. */
    val initializersOutsideArena: Boolean = false,
    /** true 면 실행마다 아레나의 비어 있는 영역을 시스템에 돌려준다. */
    val shrinkArenaAfterRun: Boolean = false,
    /**
     * true 면 세션별 스레드풀 대신 ORT 환경의 전역 스레드풀을 쓴다.
     *
     * ORT 환경은 프로세스에 하나라서, 이 프로세스에서 **처음** 만드는 환경이어야 전역 풀이
     * 생긴다. 이미 다른 곳이 환경을 만들어 뒀으면 전역 풀이 없어 세션 생성이 실패한다.
     */
    val sharedThreadPool: Boolean = false,
    /** null 이면 ORT 기본(WARNING). 아레나 확장 로그는 INFO 에서 나온다. */
    val environmentLogLevel: OrtLoggingLevel? = null,
) {

    companion object {
        /**
         * 이 앱이 세션을 열 때 쓰는 값: CPU 아레나를 끈다.
         *
         * 플립7 실측(30페이지 앱 경로, 4병렬): 아레나를 켠 채로는 Hayai 3세션이 열기 직후 353 MB,
         * 첫 페이지 뒤 803 MB 를 쥐고 처리 중 최대 PSS 가 873~904 MB 였다. 두 배 확장 전략이
         * 파일 내장 초기화값 85 MB 를 127 MB 로, 4병렬 활성값 패턴 블록을 +450 MB 로 잡아 두고
         * 세션이 닫힐 때까지 돌려주지 않기 때문이다. 아레나를 끄면 220 / 285 MB, 최대 525~527 MB.
         * YOLO 세션도 같은 이유로 유휴 상태 136 MB → 10 MB. 값은 짝 비교 2회에서 +2.5~5% 느렸고
         * 출력은 60/60 페이지 동일했다. 메모리 패턴까지 끄면 +9% 로 더 느려 패턴은 켜 둔다.
         */
        val NO_ARENA = OrtMemoryOptions(cpuArena = false)
    }

    /** 이 옵션에 맞는 ORT 환경. 이미 만들어진 환경이 있으면 ORT 가 그것을 그대로 돌려준다. */
    fun environment(intraOpThreads: Int): OrtEnvironment {
        val level = environmentLogLevel ?: OrtLoggingLevel.ORT_LOGGING_LEVEL_WARNING
        if (!sharedThreadPool) return OrtEnvironment.getEnvironment(level)
        return OrtEnvironment.ThreadingOptions().use { threading ->
            if (intraOpThreads > 0) threading.setGlobalIntraOpNumThreads(intraOpThreads)
            threading.setGlobalInterOpNumThreads(1)
            OrtEnvironment.getEnvironment(level, OrtEnvironment.DEFAULT_NAME, threading)
        }
    }

    /** 세션 옵션에 반영한다. [intraOpThreads] 가 0 이면 ORT 기본값(코어 수)을 쓴다. */
    fun configure(options: OrtSession.SessionOptions, intraOpThreads: Int) {
        if (sharedThreadPool) {
            options.disablePerSessionThreads()
        } else if (intraOpThreads > 0) {
            options.setIntraOpNumThreads(intraOpThreads)
        }
        if (!cpuArena) options.setCPUArenaAllocator(false)
        if (!memoryPattern) options.setMemoryPatternOptimization(false)
        if (!prepacking) options.addConfigEntry("session.disable_prepacking", "1")
        if (initializersOutsideArena) {
            options.addConfigEntry("session.use_device_allocator_for_initializers", "1")
        }
    }

    /** 실행마다 넘길 옵션. 필요 없으면 null. 받은 쪽이 닫는다. */
    fun newRunOptions(): OrtSession.RunOptions? {
        if (!shrinkArenaAfterRun) return null
        return OrtSession.RunOptions().apply {
            addRunConfigEntry("memory.enable_memory_arena_shrinkage", "cpu:0")
        }
    }
}
