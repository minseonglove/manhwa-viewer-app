import SwiftUI
import ComposeApp

/// Runs the OCR pipeline A/B on launch and leaves the results in Documents/ocr-eval/out.
/// Launch with `--attest-eval` to instead run the App Attest harness against Documents/attest-eval.
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
                    if ProcessInfo.processInfo.arguments.contains("--attest-eval") {
                        let dir = documents + "/attest-eval"
                        try? FileManager.default.createDirectory(atPath: dir, withIntermediateDirectories: true)
                        DispatchQueue.global(qos: .userInitiated).async {
                            let result = AttestationEvalHarness.shared.run(dir: dir)
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
