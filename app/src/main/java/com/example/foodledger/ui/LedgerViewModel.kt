package com.example.foodledger.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodledger.data.LedgerEntry
import com.example.foodledger.data.LedgerRepository
import com.example.foodledger.recognition.FoodRecognitionService
import com.example.foodledger.recognition.ModelProvider
import com.example.foodledger.recognition.ModelSettingsRepository
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
    val selectedProvider: ModelProvider = ModelProvider.OPENAI,
    val providerKeys: Map<ModelProvider, String> = emptyMap()
)

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LedgerRepository(application)
    private val settings = ModelSettingsRepository(application)
    private val recognition = FoodRecognitionService(application)
    private val _state = MutableStateFlow(LedgerUiState(
        entries = repository.load(),
        selectedProvider = settings.selectedProvider(),
        providerKeys = ModelProvider.entries.associateWith(settings::getKey)
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
            runCatching {
                val provider = _state.value.selectedProvider
                recognition.recognize(images, provider, _state.value.providerKeys[provider].orEmpty())
            }.onSuccess { result ->
                _state.value = _state.value.copy(
                    recognizing = false,
                    recognizedMeal = result.meal,
                    recognizedCategory = result.category,
                    recognizedAmount = if (result.suggestedAmount > 0) result.suggestedAmount.toString() else ""
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    recognizing = false,
                    recognitionError = error.message ?: "识别失败，请检查网络和 Key"
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

    fun consumeRecognition() {
        _state.value = _state.value.copy(
            recognizedMeal = "",
            recognizedCategory = "餐饮",
            recognizedAmount = "",
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
