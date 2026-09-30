package com.example.foodledger.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodledger.data.LedgerEntry
import com.example.foodledger.data.LedgerRepository
import com.example.foodledger.recognition.FoodRecognitionService
import com.example.foodledger.recognition.ModelProvider
import com.example.foodledger.recognition.ModelRequestLog
import com.example.foodledger.recognition.ModelSettingsRepository
import com.example.foodledger.recognition.RequestLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LedgerUiState(
    val entries: List<LedgerEntry> = emptyList(),
    val selectedImages: List<Uri> = emptyList(),
    val recognizing: Boolean = false,
    val recognitionError: String = "",
    val recognizedMeal: String = "",
    val recognizedCategory: String = "餐饮",
    val recognizedAmount: String = "",
    val recognizedNote: String = "",
    val selectedProvider: ModelProvider = ModelProvider.OPENAI,
    val providerKeys: Map<ModelProvider, String> = emptyMap(),
    val recognitionPrompt: String = ModelSettingsRepository.DEFAULT_PROMPT,
    val requestLogs: List<ModelRequestLog> = emptyList()
)

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LedgerRepository(application)
    private val settings = ModelSettingsRepository(application)
    private val requestLogRepository = RequestLogRepository(application)
    private val recognition = FoodRecognitionService(application)
    private val _state = MutableStateFlow(LedgerUiState(
        entries = repository.load(),
        selectedProvider = settings.selectedProvider(),
        providerKeys = ModelProvider.entries.associateWith(settings::getKey),
        recognitionPrompt = settings.recognitionPrompt(),
        requestLogs = requestLogRepository.load()
    ))
    val state: StateFlow<LedgerUiState> = _state.asStateFlow()

    fun setImages(images: List<Uri>) {
        _state.value = _state.value.copy(selectedImages = images)
    }

    fun removeImage(uri: Uri) {
        _state.value = _state.value.copy(selectedImages = _state.value.selectedImages - uri)
    }

    fun recognizeImages() {
        val images = _state.value.selectedImages
        if (images.isEmpty() || _state.value.recognizing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(recognizing = true, recognitionError = "")
            val started = System.currentTimeMillis()
            val provider = _state.value.selectedProvider
            val prompt = _state.value.recognitionPrompt
            runCatching {
                recognition.recognize(images, provider, _state.value.providerKeys[provider].orEmpty(), prompt)
            }.onSuccess { result ->
                _state.value = _state.value.copy(
                    recognizing = false,
                    recognizedMeal = result.title,
                    recognizedCategory = result.category,
                    recognizedAmount = if (result.amount > 0) result.amount.toString() else "",
                    recognizedNote = result.description,
                    requestLogs = requestLogRepository.add(ModelRequestLog(
                        id = started, timestamp = started, provider = provider.displayName,
                        model = provider.modelName, imageCount = images.size, prompt = prompt,
                        response = result.rawResponse, error = "", durationMs = System.currentTimeMillis() - started
                    ))
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    recognizing = false,
                    recognitionError = error.message ?: "识别失败，请检查网络和 Key",
                    requestLogs = requestLogRepository.add(ModelRequestLog(
                        id = started, timestamp = started, provider = provider.displayName,
                        model = provider.modelName, imageCount = images.size, prompt = prompt,
                        response = "", error = error.message ?: "未知错误", durationMs = System.currentTimeMillis() - started
                    ))
                )
            }
        }
    }

    fun selectProvider(provider: ModelProvider) {
        settings.select(provider)
        _state.value = _state.value.copy(selectedProvider = provider)
    }

    fun saveProviderKey(provider: ModelProvider, key: String) {
        settings.saveKey(provider, key)
        _state.value = _state.value.copy(providerKeys = _state.value.providerKeys + (provider to key.trim()))
    }

    fun saveRecognitionPrompt(prompt: String) {
        settings.saveRecognitionPrompt(prompt)
        _state.value = _state.value.copy(recognitionPrompt = prompt)
    }

    fun clearRequestLogs() {
        requestLogRepository.clear()
        _state.value = _state.value.copy(requestLogs = emptyList())
    }

    fun consumeRecognition() {
        _state.value = _state.value.copy(
            recognizedMeal = "",
            recognizedCategory = "餐饮",
            recognizedAmount = "",
            recognizedNote = "",
            recognitionError = ""
        )
    }

    fun add(meal: String, amount: Double, category: String, note: String) {
        val entry = LedgerEntry(
            meal = meal.trim(),
            amount = amount,
            category = category,
            note = note.trim(),
            imageUris = _state.value.selectedImages.map(Uri::toString)
        )
        val updated = listOf(entry) + _state.value.entries
        repository.save(updated)
        _state.value = _state.value.copy(entries = updated, selectedImages = emptyList())
    }

    fun delete(id: Long) {
        val updated = _state.value.entries.filterNot { it.id == id }
        repository.save(updated)
        _state.value = _state.value.copy(entries = updated)
    }
}
