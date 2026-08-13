package no.fdk.dataset.preview.service

import no.fdk.dataset.preview.model.ErrorType

class PreviewException(message: String?, val errorType: ErrorType = ErrorType.INTERNAL_ERROR) : Exception(message)
