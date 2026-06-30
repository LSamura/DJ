package com.djassistant.feature.feedback

interface FeedbackManager {
    fun onActivation()
    fun onSuccess()
    fun onError()
}
