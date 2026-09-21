import SwiftUI
import ComposeApp

/// Runs the OCR pipeline A/B on launch and leaves the results in Documents/ocr-eval/out.
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
