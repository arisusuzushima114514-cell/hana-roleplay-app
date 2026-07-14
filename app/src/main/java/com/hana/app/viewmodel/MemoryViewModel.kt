package com.hana.app.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hana.app.data.db.entity.MemoryEntryEntity
import com.hana.app.data.repository.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemoryUiState(
    val items: List<MemoryEntryEntity> = emptyList(),
    val summary: String = "主助手会把值得长期记住的事实、偏好和进行中的事项保存在本地。"
)

class MemoryViewModel(
    private val repository: MemoryRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.observeMainMemory().collect { items ->
                _uiState.update {
                    it.copy(
                        items = items,
                        summary = if (items.isEmpty()) {
                            "当前还没有提炼到长期记忆。"
                        } else {
                            "当前共保存 ${items.size} 条主助手长期记忆。"
                        }
                    )
                }
            }
        }
    }

    fun togglePinned(item: MemoryEntryEntity) {
        viewModelScope.launch { repository.togglePinned(item.id, item.isPinned) }
    }

    fun archive(item: MemoryEntryEntity) {
        viewModelScope.launch { repository.archive(item.id) }
    }

    fun update(item: MemoryEntryEntity, newContent: String, newType: String) {
        viewModelScope.launch {
            repository.update(item, newContent, newType)
            _events.emit("记忆已更新")
        }
    }

    fun export(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = repository.exportMainMemoryJson()
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    ?: error("无法写入记忆文件")
            }.onSuccess {
                _events.emit("主助手记忆已导出")
            }.onFailure {
                _events.emit("导出失败: ${it.message.orEmpty()}")
            }
        }
    }

    fun import(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("无法读取记忆文件")
                repository.importMainMemoryJson(json)
            }.onSuccess {
                _events.emit("已导入 $it 条主助手记忆")
            }.onFailure {
                _events.emit("导入失败: ${it.message.orEmpty()}")
            }
        }
    }
}
