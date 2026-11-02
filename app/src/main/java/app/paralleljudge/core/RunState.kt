package app.paralleljudge.core

sealed interface ModelState {
    data object Pending : ModelState
    data class Done(val text: String, val elapsedMs: Long) : ModelState
    data class Failed(
        val error: String,
        val kind: LlmException.Kind,
        val partialText: String = ""
    ) : ModelState
}

sealed interface JudgeState {
    data object Idle : JudgeState
    data class Skipped(val reason: String) : JudgeState
    data class Running(val judgeName: String, val text: String) : JudgeState
    data class Done(val judgeName: String, val text: String) : JudgeState
    data class Failed(val error: String) : JudgeState
}

data class RunState(
    val answers: Map<String, ModelState>,
    val judge: JudgeState = JudgeState.Idle,
    val judgeLabels: Map<String, String> = emptyMap(),
    val finished: Boolean = false
)

data class JudgeConfig(
    val judges: List<ModelSpec>,
    val systemPrompt: String = JudgePrompt.DEFAULT_SYSTEM,
    val maxTokens: Int = 1500,
    val temperature: Double? = 0.2,
    val anonymize: Boolean = true,
    val shuffle: Boolean = true,
    val maxCharsPerAnswer: Int = 6_000,
    val localMaxCharsPerAnswer: Int = 2_500,
    val minAnswers: Int = 2
)

data class RunRequest(
    val prompt: String,
    val systemPrompt: String? = null,
    val models: List<ModelSpec>,
    val judge: JudgeConfig? = null,
    val maxAttempts: Int = 3
)
