package no.fdk.dataset.preview.service

import org.slf4j.Logger

internal fun Logger.logDebug(message: String) {
    if (isDebugEnabled) {
        debug(message)
    }
}

internal fun Logger.logDebug(message: String, throwable: Throwable?) {
    if (isDebugEnabled) {
        debug(message, throwable)
    }
}
