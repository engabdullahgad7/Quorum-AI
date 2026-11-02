package app.paralleljudge.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.paralleljudge.core.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ParallelViewModel @Inject constructor(
    private val orchestrator: ParallelOrchestrator
) : ViewModel() {

    private val _uiState = MutableStateFlow(RunState(answers = emptyMap()))
    val uiState: StateFlow<RunState> = _uiState.asStateFlow()

    fun execute() {
        val models = listOf(
            ModelSpec("1", "Groq Llama3", Dialect.OPENAI_COMPAT, baseUrl = "https://api.groq.com/openai/v1/chat/completions", modelName = "llama3-8b-8192", apiKey = "YOUR_GROQ_KEY"),
            ModelSpec("2", "Gemini Flash", Dialect.OPENAI_COMPAT, baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", modelName = "gemini-1.5-flash", apiKey = "YOUR_GEMINI_KEY"),
            ModelSpec("3", "Local Model", Dialect.LOCAL_LITERT, modelName = "local-path")
        )
        
        val judge = ModelSpec("judge", "GPT-4o Judge", Dialect.OPENAI_COMPAT, baseUrl = "https://api.openai.com/v1/chat/completions", modelName = "gpt-4o", apiKey = "YOUR_OPENAI_KEY")

        val request = RunRequest(
            prompt = "ما هي عاصمة فرنسا؟",
            models = models,
            judge = JudgeConfig(judges = listOf(judge))
        )
        
        viewModelScope.launch {
            orchestrator.run(request).collect { state ->
                _uiState.value = state
            }
        }
    }
}
