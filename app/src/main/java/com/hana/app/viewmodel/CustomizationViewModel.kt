package com.hana.app.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hana.app.data.customization.CustomizationRepository
import com.hana.app.data.customization.CustomizationSettings
import com.hana.app.data.customization.DesktopShortcutResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CustomizationViewModel(private val repository: CustomizationRepository) : ViewModel() {
    val uiState = repository.settingsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CustomizationSettings())

    fun saveDisplayName(value: String) = viewModelScope.launch { repository.saveDisplayName(value) }
    fun saveSplashDuration(value: Int) = viewModelScope.launch { repository.saveSplashDuration(value) }
    fun saveCroppedSplash(bitmap: Bitmap, onResult: (Boolean) -> Unit) = viewModelScope.launch { onResult(repository.saveCroppedSplash(bitmap)) }
    fun clearSplash() = viewModelScope.launch { repository.clearSplash() }
    fun saveGenerationHaptic(enabled: Boolean) = viewModelScope.launch { repository.saveGenerationHaptic(enabled) }
    fun saveChatDisplay(fontSize: String, density: String, bubbleWidthPercent: Int, showAvatars: Boolean, showTime: Boolean, showBottomBarLabels: Boolean, inputBarSize: String, inputBarWidthPercent: Int) = viewModelScope.launch {
        repository.saveChatDisplay(fontSize, density, bubbleWidthPercent, showAvatars, showTime, showBottomBarLabels, inputBarSize, inputBarWidthPercent)
    }
    fun clearNavigationIcons() = viewModelScope.launch { repository.clearNavigationIcons() }
    fun saveCroppedAsset(slot: String, bitmap: Bitmap, onResult: (Boolean) -> Unit) = viewModelScope.launch { onResult(repository.saveCroppedAsset(slot, bitmap)) }
    fun clearBubbleImages() = viewModelScope.launch { repository.clearBubbleImages() }
    fun clearAsset(slot: String) = viewModelScope.launch { repository.clearAsset(slot) }
    fun saveBubbleFixedEdge(slot: String, percent: Int) = viewModelScope.launch { repository.saveBubbleFixedEdge(slot, percent) }
    fun requestDesktopShortcut(name: String): DesktopShortcutResult = repository.requestDesktopShortcut(name)
}
