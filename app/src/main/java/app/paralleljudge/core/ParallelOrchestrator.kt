package app.paralleljudge.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

class ParallelOrchestrator(
    private val resolver: ClientResolver,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val random: Random = Random.Default,
    private val backoff: suspend (attempt: Int, retryAfterMs: Long?) -> Unit = { attempt, hint ->
        delay(hint ?: (800L shl (attempt - 1)).coerceAtMost(8_000L))
    }
) {
    fun run(req: RunRequest): Flow<RunState> = channelFlow {
        require(req.models.isNotEmpty()) { "No models selected" }

        val lock = Mutex()
        var state = RunState(answers = req.models.associate { it.id to ModelState.Pending })

        suspend fun update(transform: (RunState) -> RunState) {
            lock.withLock {
                state = transform(state)
                send(state)
            }
        }
        send(state)

        supervisorScope {
            req.models.map { spec ->
                async {
                    runModel(spec, req) { modelState ->
                        update { it.copy(answers = it.answers + (spec.id to modelState)) }
                    }
                }
            }.awaitAll()
        }

        req.judge?.let { cfg -> runJudge(req, cfg, state) { transform -> update(transform) } }

        update { it.copy(finished = true) }
    }.conflate()

    private suspend fun runModel(spec: ModelSpec, req: RunRequest, report: suspend (ModelState) -> Unit) {
        val started = clock()
        val messages = buildList {
            req.systemPrompt?.takeIf { it.isNotBlank() }?.let { add(ChatMessage(Role.SYSTEM, it)) }
            add(ChatMessage(Role.USER, req.prompt))
        }
        val llmReq = LlmRequest(messages, spec.maxTokens, spec.temperature)

        try {
            val client = resolver.clientFor(spec)
            val sb = StringBuilder()
            var lastError: LlmException? = null

            for (attempt in 1..req.maxAttempts) {
                try {
                    sb.setLength(0)
                    client.stream(llmReq).collect { chunk ->
                        sb.append(chunk)
                    }
                    // تم التصحيح هنا: استخدام length بدلاً من isEmpty()
                    if (sb.length == 0) throw LlmException("Empty response", LlmException.Kind.EMPTY)
                    report(ModelState.Done(sb.toString(), clock() - started))
                    return
                } catch (e: LlmException) {
                    lastError = e
                    if (!e.isTransient || attempt == req.maxAttempts) break
                    backoff(attempt, e.retryAfterMs)
                }
            }
            report(ModelState.Failed(lastError?.message ?: "Failed", lastError?.kind ?: LlmException.Kind.UNKNOWN, sb.toString()))
        } catch (e: Exception) {
            report(ModelState.Failed(e.message ?: "Unknown", LlmException.Kind.UNKNOWN))
        }
    }

    private suspend fun runJudge(req: RunRequest, cfg: JudgeConfig, state: RunState, update: suspend ((RunState) -> RunState) -> Unit) {
        val successes = state.answers.values.filterIsInstance<ModelState.Done>()
        if (successes.size < cfg.minAnswers) {
            update { it.copy(judge = JudgeState.Skipped("Not enough successful models")) }
            return
        }

        val answers = state.answers.entries
            .filter { it.value is ModelState.Done }
            .map { entry -> req.models.find { it.id == entry.key }!! to (entry.value as ModelState.Done).text }

        for (judgeSpec in cfg.judges) {
            try {
                update { it.copy(judge = JudgeState.Running(judgeSpec.displayName, "")) }
                val maxChars = if (judgeSpec.isLocal) cfg.localMaxCharsPerAnswer else cfg.maxCharsPerAnswer
                val prompt = JudgePrompt.build(req.prompt, answers, cfg, maxChars, random)

                val llmReq = LlmRequest(prompt.messages, cfg.maxTokens, cfg.temperature)
                val client = resolver.clientFor(judgeSpec)
                val sb = StringBuilder()

                client.stream(llmReq).collect { chunk ->
                    sb.append(chunk)
                    update { current -> current.copy(judge = JudgeState.Running(judgeSpec.displayName, sb.toString())) }
                }

                update { it.copy(judge = JudgeState.Done(judgeSpec.displayName, sb.toString()), judgeLabels = prompt.labelToModelId) }
                return
            } catch (e: Exception) {
                // جرب القاضي التالي في حال الفشل
            }
        }
        update { it.copy(judge = JudgeState.Failed("All judges failed")) }
    }
}
