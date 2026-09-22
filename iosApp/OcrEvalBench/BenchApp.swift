import SwiftUI
import ComposeApp

/// Runs the OCR pipeline A/B on launch and leaves the results in Documents/ocr-eval/out.
/// Launch with `--attest-eval` to instead run the App Attest harness against Documents/attest-eval,
/// or `--proxy-eval` to translate one line through the live proxy with App Attest.
@main
struct BenchApp: App {
    @State private var status = "running…"

    var body: some Scene {
        WindowGroup {
            Text(status)
                .padding()
                .onAppear {
                    UIApplication.shared.isIdleTimerDisabled = true
                    let documents = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true)[0]
                    let arguments = ProcessInfo.processInfo.arguments
                    if arguments.contains("--attest-eval") || arguments.contains("--proxy-eval") {
                        let dir = documents + "/attest-eval"
                        try? FileManager.default.createDirectory(atPath: dir, withIntermediateDirectories: true)
                        let proxy = arguments.contains("--proxy-eval")
                        DispatchQueue.global(qos: .userInitiated).async {
                            let result = proxy ? AttestationEvalHarness.shared.runProxy(dir: dir) : AttestationEvalHarness.shared.run(dir: dir)
                            DispatchQueue.main.async { status = result }
                        }
                    } else {
                        DispatchQueue.global(qos: .userInitiated).async {
                            let result = OcrEvalHarness.shared.runPipelineAb(
                                evalDir: documents + "/ocr-eval",
                                outDir: documents + "/ocr-eval/out"
                            )
                            DispatchQueue.main.async { status = result }
                        }
                    }
                }
        }
    }
}
