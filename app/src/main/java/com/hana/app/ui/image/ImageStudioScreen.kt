package com.hana.app.ui.image

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.hana.app.R
import com.hana.app.viewmodel.ImageConversationItem
import com.hana.app.viewmodel.ImageGenerationUiState
import kotlinx.coroutines.flow.SharedFlow

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ImageStudioScreen(
    uiState: ImageGenerationUiState,
    events: SharedFlow<String>,
    onInputChange: (String) -> Unit,
    onStyleChange: (String) -> Unit,
    onRatioChange: (String) -> Unit,
    onBatchCountChange: (Int) -> Unit,
    onNegativePromptChange: (String) -> Unit,
    onReferenceImagesPicked: (List<Uri>) -> Unit,
    onRemoveReferenceImage: (String) -> Unit,
    onClearReferenceImages: () -> Unit,
    onGenerate: () -> Unit,
    onStopGeneration: () -> Unit,
    onSaveImage: (String) -> Unit,
    onReusePrompt: (ImageConversationItem) -> Unit,
    onContinueFromImage: (ImageConversationItem, String) -> Unit,
    modifier: Modifier = Modifier
) {
    ImageChatPanel(
        uiState = uiState,
        events = events,
        onInputChange = onInputChange,
        onStyleChange = onStyleChange,
        onRatioChange = onRatioChange,
        onBatchCountChange = onBatchCountChange,
        onNegativePromptChange = onNegativePromptChange,
        onReferenceImagesPicked = onReferenceImagesPicked,
        onRemoveReferenceImage = onRemoveReferenceImage,
        onClearReferenceImages = onClearReferenceImages,
        onGenerate = onGenerate,
        onStopGeneration = onStopGeneration,
        onSaveImage = onSaveImage,
        onReusePrompt = onReusePrompt,
        onContinueFromImage = onContinueFromImage,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ImageChatPanel(
    uiState: ImageGenerationUiState,
    events: SharedFlow<String>,
    onInputChange: (String) -> Unit,
    onStyleChange: (String) -> Unit,
    onRatioChange: (String) -> Unit,
    onBatchCountChange: (Int) -> Unit,
    onNegativePromptChange: (String) -> Unit,
    onReferenceImagesPicked: (List<Uri>) -> Unit,
    onRemoveReferenceImage: (String) -> Unit,
    onClearReferenceImages: () -> Unit,
    onGenerate: () -> Unit,
    onStopGeneration: () -> Unit,
    onSaveImage: (String) -> Unit,
    onReusePrompt: (ImageConversationItem) -> Unit,
    onContinueFromImage: (ImageConversationItem, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingImageToSave by remember { mutableStateOf<String?>(null) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending = pendingImageToSave
        pendingImageToSave = null
        if (granted && pending != null) onSaveImage(pending)
        else if (!granted) Toast.makeText(context, "Android 9 及以下保存图片需要存储权限", Toast.LENGTH_SHORT).show()
    }
    fun saveImage(url: String) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingImageToSave = url
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            onSaveImage(url)
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) onReferenceImagesPicked(uris)
    }

    LaunchedEffect(events) {
        events.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HeroCard(uiState)
            }
            item {
                PromptComposer(
                    uiState = uiState,
                    onInputChange = onInputChange,
                    onStyleChange = onStyleChange,
                    onRatioChange = onRatioChange,
                    onBatchCountChange = onBatchCountChange,
                    onNegativePromptChange = onNegativePromptChange,
                    onPickReferenceImages = { imagePicker.launch("image/*") },
                    onRemoveReferenceImage = onRemoveReferenceImage,
                    onClearReferenceImages = onClearReferenceImages,
                    onGenerate = onGenerate,
                    onStopGeneration = onStopGeneration
                )
            }
            items(uiState.messages, key = { it.id }) { message ->
                GeneratedMessageCard(
                    message = message,
                    onSaveImage = ::saveImage,
                    onReusePrompt = onReusePrompt,
                    onContinueFromImage = onContinueFromImage
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun HeroCard(uiState: ImageGenerationUiState) {
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.image_badge), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(stringResource(R.string.image_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.image_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (uiState.draftSourceLabel.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                ) {
                    Text(
                        uiState.draftSourceLabel,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.64f)) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Palette, contentDescription = null, modifier = Modifier.height(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.image_style_preset), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.64f)) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Collections, contentDescription = null, modifier = Modifier.height(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.image_batch_mode), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text(
                stringResource(R.string.image_provider_status, uiState.imageProviderName, uiState.imageModelName.ifBlank { stringResource(R.string.image_model_unset) }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(uiState.statusText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if (uiState.creativePresetEnabled) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "当前已注入创作预设",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            "本轮会在请求送进模型前注入一次，模型返回 revised prompt 后也会再套一遍，避免预设中途丢失。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        if (uiState.creativePresetSummary.isNotBlank()) {
                            Text(
                                "预设摘要：${uiState.creativePresetSummary}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
            if (uiState.isGenerating) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                    Text(uiState.generationProgressText.ifBlank { stringResource(R.string.image_generating) }, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PromptComposer(
    uiState: ImageGenerationUiState,
    onInputChange: (String) -> Unit,
    onStyleChange: (String) -> Unit,
    onRatioChange: (String) -> Unit,
    onBatchCountChange: (Int) -> Unit,
    onNegativePromptChange: (String) -> Unit,
    onPickReferenceImages: () -> Unit,
    onRemoveReferenceImage: (String) -> Unit,
    onClearReferenceImages: () -> Unit,
    onGenerate: () -> Unit,
    onStopGeneration: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(R.string.image_prompt_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = uiState.input,
                onValueChange = onInputChange,
                placeholder = { Text(stringResource(R.string.image_prompt_placeholder)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OptionRow(
                        title = "风格",
                        values = ImageStyles.presets.map { it.labelZh },
                        selected = uiState.selectedStyle,
                        onSelected = onStyleChange
                    )
                    OptionRow(
                        title = "画幅",
                        values = listOf("1:1", "4:5", "3:4", "2:3", "9:16", "4:3", "3:2", "16:9", "21:9", "2:1", "1:2", "5:4"),
                        selected = uiState.selectedRatio,
                        onSelected = onRatioChange
                    )
                    BatchRow(uiState.batchCount, onBatchCountChange)
                }
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.image_reference_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.image_reference_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (uiState.referenceImages.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.referenceImages.forEach { reference ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                                ) {
                                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        AsyncImage(
                                            model = reference.uri,
                                            contentDescription = null,
                                            modifier = Modifier.width(108.dp).height(108.dp),
                                            contentScale = ContentScale.Crop
                                        )
                                        TextButton(onClick = { onRemoveReferenceImage(reference.uri) }) {
                                            Text(stringResource(R.string.remove))
                                        }
                                    }
                                }
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onPickReferenceImages, shape = RoundedCornerShape(14.dp)) { Text("继续添加参考图") }
                            Button(onClick = onClearReferenceImages, shape = RoundedCornerShape(14.dp), colors = androidx.compose.material3.ButtonDefaults.buttonColors()) { Text(stringResource(R.string.image_reference_remove)) }
                        }
                    } else {
                        Button(onClick = onPickReferenceImages, shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Filled.Image, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("添加参考图")
                        }
                    }
                }
            }
            OutlinedTextField(
                value = uiState.negativePromptZh,
                onValueChange = onNegativePromptChange,
                label = { Text(stringResource(R.string.image_constraints_label)) },
                placeholder = { Text(stringResource(R.string.image_constraints_placeholder)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            if (uiState.isGenerating) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onStopGeneration,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("停止生成")
                    }
                }
            } else {
                Button(
                    onClick = onGenerate,
                    enabled = uiState.input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(stringResource(R.string.image_generate_action))
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun OptionRow(
    title: String,
    values: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { value ->
                FilterChip(selected = selected == value, onClick = { onSelected(value) }, label = { Text(value) })
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BatchRow(count: Int, onSelected: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.image_batch_label), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 2, 4, 8).forEach { value ->
                FilterChip(selected = count == value, onClick = { onSelected(value) }, label = { Text(value.toString()) })
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun GeneratedMessageCard(
    message: ImageConversationItem,
    onSaveImage: (String) -> Unit,
    onReusePrompt: (ImageConversationItem) -> Unit,
    onContinueFromImage: (ImageConversationItem, String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(message.promptZh, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (!message.isLoading) {
                    TextButton(onClick = { onReusePrompt(message) }) {
                        Text(stringResource(R.string.image_edit_continue))
                    }
                }
            }
            Text(message.promptEn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (message.negativePromptZh.isNotBlank()) {
                Text(
                    stringResource(R.string.image_constraints_summary, message.negativePromptZh),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (message.isLoading) {
                Text(stringResource(R.string.image_generating), color = MaterialTheme.colorScheme.primary)
            }
            message.errorMessage?.let {
                Text(stringResource(R.string.image_error_prefix, it), color = MaterialTheme.colorScheme.error)
            }
            if (message.imageUrls.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    message.imageUrls.forEach { url ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(260.dp),
                                    contentScale = ContentScale.Crop
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(onClick = { onSaveImage(url) }, shape = RoundedCornerShape(12.dp)) {
                                        Text(stringResource(R.string.image_save_album))
                                    }
                                    Button(onClick = { onContinueFromImage(message, url) }, shape = RoundedCornerShape(12.dp)) {
                                        Text(stringResource(R.string.image_continue_from_result))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
