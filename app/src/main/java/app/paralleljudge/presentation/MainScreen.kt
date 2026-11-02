package app.paralleljudge.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.paralleljudge.core.ModelState
import app.paralleljudge.core.JudgeState

@Composable
fun MainScreen(viewModel: ParallelViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(
            onClick = { viewModel.execute() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("بدء المقارنة والتحكيم")
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.answers.values.toList()) { modelState ->
                ModelCard(modelState)
            }
            if (state.judge !is JudgeState.Idle) {
                item {
                    JudgeCard(state.judge)
                }
            }
        }
    }
}

@Composable
fun ModelCard(state: ModelState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (state) {
                is ModelState.Pending -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                is ModelState.Done -> {
                    Text(text = state.text)
                    Text(text = "الوقت: ${state.elapsedMs}ms", style = MaterialTheme.typography.labelSmall)
                }
                is ModelState.Failed -> Text(text = "خطأ: ${state.error}", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun JudgeCard(state: JudgeState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("👑 حكم المحكم", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            when (state) {
                is JudgeState.Running -> Text(state.text.ifEmpty { "جاري التحليل..." })
                is JudgeState.Done -> Text(state.text)
                is JudgeState.Failed -> Text("فشل التحكيم: ${state.error}", color = MaterialTheme.colorScheme.error)
                else -> {}
            }
        }
    }
}
