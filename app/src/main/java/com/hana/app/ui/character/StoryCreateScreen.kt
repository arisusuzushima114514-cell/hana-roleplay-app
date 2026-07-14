package com.hana.app.ui.character

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hana.app.R

private val storyStylePresets = listOf("电影感叙事", "轻小说", "悬疑推理", "奇幻冒险", "治愈日常", "黑暗史诗")
private val storyLengthPresets = listOf("短回合", "中篇推进", "长线慢烧")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StoryCreateScreen(
    onBack: () -> Unit,
    onStartStory: (title: String, premise: String, style: String, length: String, model: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var premise by remember { mutableStateOf("") }
    var style by remember { mutableStateOf(storyStylePresets.first()) }
    var length by remember { mutableStateOf(storyLengthPresets[1]) }
    var model by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.story_create_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.story_workshop), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text(stringResource(R.string.story_opening_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.story_opening_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            StoryCard(stringResource(R.string.story_title_label)) {
                OutlinedTextField(title, { title = it }, placeholder = { Text(stringResource(R.string.story_title_placeholder)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }

            StoryCard(stringResource(R.string.story_premise_label)) {
                OutlinedTextField(
                    value = premise,
                    onValueChange = { premise = it },
                    placeholder = { Text(stringResource(R.string.story_premise_placeholder)) },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            StoryCard(stringResource(R.string.story_style_label)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    storyStylePresets.forEach { preset: String ->
                        FilterChip(selected = style == preset, onClick = { style = preset }, label = { Text(preset) })
                    }
                }
            }

            StoryCard(stringResource(R.string.story_length_label)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    storyLengthPresets.forEach { preset: String ->
                        FilterChip(selected = length == preset, onClick = { length = preset }, label = { Text(preset) })
                    }
                }
            }

            StoryCard(stringResource(R.string.story_model_optional_label)) {
                OutlinedTextField(model, { model = it }, placeholder = { Text(stringResource(R.string.story_model_optional_placeholder)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }

            Button(
                onClick = { onStartStory(title.trim(), premise.trim(), style, length, model.trim()) },
                enabled = title.isNotBlank() && premise.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.story_start))
            }
        }
    }
}

@Composable
private fun StoryCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                content()
            }
        )
    }
}
