package com.manhwaviewer.data.ocr.inference

/**
 * Platform-specific wrapper for encoder output data.
 * Avoids fp32-fp16 round-trip by keeping data in the native format.
 *
 * Android: wraps fp16 ByteBuffer (ONNX Runtime native output)
 * iOS: wraps MLMultiArray (CoreML native output)
 */
expect class EncoderOutput
