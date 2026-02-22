package com.example.espion32.model

sealed class PcapTransferState {
    object Idle : PcapTransferState()
    data class Receiving(val chunksReceived: Int, val totalSize: Int) : PcapTransferState()
    data class Done(val filePath: String) : PcapTransferState()
    data class Error(val reason: String) : PcapTransferState()
}