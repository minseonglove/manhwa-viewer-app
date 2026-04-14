package com.manhwaviewer.data.ocr

fun floatToHalf(value: Float): Short {
    val bits = value.toRawBits()
    val sign = (bits ushr 16) and 0x8000
    val exponent = (bits ushr 23) and 0xFF
    val mantissa = bits and 0x7FFFFF

    return when {
        exponent == 0 -> sign.toShort()
        exponent == 0xFF -> (sign or 0x7C00 or if (mantissa != 0) 0x200 else 0).toShort()
        else -> {
            val newExp = exponent - 127 + 15
            when {
                newExp >= 31 -> (sign or 0x7C00).toShort()
                newExp <= 0 -> sign.toShort()
                else -> (sign or (newExp shl 10) or (mantissa ushr 13)).toShort()
            }
        }
    }
}

fun halfToFloat(value: Short): Float {
    val bits = value.toInt() and 0xFFFF
    val sign = (bits and 0x8000) shl 16
    val exponent = (bits and 0x7C00) ushr 10
    val mantissa = bits and 0x03FF

    return when {
        exponent == 0 -> Float.fromBits(sign)
        exponent == 31 -> Float.fromBits(sign or 0x7F800000 or (mantissa shl 13))
        else -> Float.fromBits(sign or ((exponent - 15 + 127) shl 23) or (mantissa shl 13))
    }
}
