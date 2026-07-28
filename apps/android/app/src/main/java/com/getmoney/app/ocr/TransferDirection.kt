package com.getmoney.app.ocr

enum class TransferDirection {
    OUT,
    IN,
    ;

    fun labelTh(): String = when (this) {
        OUT -> "โอนออก"
        IN -> "รับเข้า"
    }

    companion object {
        fun fromLabelTh(line: String): TransferDirection? = when (line.trim()) {
            "โอนออก" -> OUT
            "รับเข้า" -> IN
            else -> null
        }
    }
}
