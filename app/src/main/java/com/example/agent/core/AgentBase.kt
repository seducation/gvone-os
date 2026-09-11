package com.example.agent.core

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Token for cooperative task cancellation.
 */
class CancellationToken {
    private val _isCancelled = AtomicBoolean(false)
    val isCancelled: Boolean get() = _isCancelled.get()

    fun cancel() {
        _isCancelled.set(true)
    }
}

class CancelledException(message: String = "Task was cancelled") : RuntimeException(message)

/**
 * Abstract base class for all GVONE agents, ported and adapted from KAI.KAMUI.GVONE.
 * Enforces automatic step logging, metabolic stress tracking, pause/resume, and delegation.
 */
abstract class AgentBase(
    override val name: String,
    val logger: StepLogger = StepLogger.global
) : Agent {

    protected var currentStatus: AgentStatus = AgentStatus.IDLE
    protected var lastResult: AgentResult? = null
    protected var failureCount: Int = 0
    protected var isQuarantined: Boolean = false

    var metabolicStress: Double = 0.0
        protected set

    private var activeJob: Job? = null
    private var activeToken: CancellationToken? = null

    private val pauseMutex = Mutex()
    private var isPaused = false
    private var resumeCompleter: CompletableDeferred<Unit>? = null

    override fun identity(): String = name

    override fun status(): AgentStatus = currentStatus

    override fun health(): AgentHealth = AgentHealth(
        agentName = name,
        metabolicStress = metabolicStress,
        failureCount = failureCount,
        isQuarantined = isQuarantined,
        status = currentStatus
    )

    override fun result(): AgentResult? = lastResult

    override fun cancel() {
        activeToken?.cancel()
        activeJob?.cancel()
        currentStatus = AgentStatus.CANCELLED
        logger.logInstant(
            agentName = name,
            action = StepType.CANCELLED,
            target = "Execution cancelled by user or system",
            status = StepStatus.FAILED
        )
    }

    fun pause() {
        synchronized(this) {
            isPaused = true
            resumeCompleter = CompletableDeferred()
            currentStatus = AgentStatus.PAUSED
        }
        logger.logInstant(
            agentName = name,
            action = StepType.WAITING,
            target = "Agent execution paused",
            status = StepStatus.PENDING
        )
    }

    fun resume() {
        synchronized(this) {
            isPaused = false
            resumeCompleter?.complete(Unit)
            resumeCompleter = null
            currentStatus = AgentStatus.EXECUTING
        }
        logger.logInstant(
            agentName = name,
            action = StepType.DECIDE,
            target = "Agent execution resumed",
            status = StepStatus.SUCCESS
        )
    }

    protected suspend fun waitIfPaused() {
        val completer = synchronized(this) { if (isPaused) resumeCompleter else null }
        completer?.await()
    }

    fun throwIfCancelled() {
        if (activeToken?.isCancelled == true) {
            throw CancelledException("Agent $name was cancelled.")
        }
    }

    /**
     * Execute an action with automatic transparent step logging.
     * Enforces: action -> log -> result.
     */
    suspend fun <T> executeAction(
        action: StepType,
        target: String,
        metadata: Map<String, Any?>? = null,
        task: suspend () -> T
    ): T {
        waitIfPaused()
        throwIfCancelled()

        val startTime = System.currentTimeMillis()
        // Increase metabolic stress proportionally with effort
        metabolicStress = (metabolicStress + 0.05).coerceIn(0.0, 1.0)

        val step = logger.startStep(
            agentName = name,
            action = action,
            target = target,
            metadata = metadata
        )

        return try {
            val result = task()
            throwIfCancelled()
            val duration = System.currentTimeMillis() - startTime
            logger.completeStep(
                stepId = step.stepId,
                durationMs = duration,
                metadata = mapOf("success" to true)
            )
            result
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            if (e is CancelledException) {
                logger.failStep(
                    stepId = step.stepId,
                    errorMessage = "Interrupted / Cancelled",
                    durationMs = duration
                )
            } else {
                failureCount++
                metabolicStress = (metabolicStress + 0.1).coerceIn(0.0, 1.0)
                logger.failStep(
                    stepId = step.stepId,
                    errorMessage = e.message ?: e.toString(),
                    durationMs = duration
                )
            }
            throw e
        }
    }

    /**
     * Executes a series of tasks in sequential order.
     */
    suspend fun <T> executeSequence(
        tasks: List<suspend () -> T>
    ): List<T> {
        val results = mutableListOf<T>()
        for (task in tasks) {
            results.add(task())
        }
        return results
    }

    /**
     * Executes tasks concurrently in parallel.
     */
    suspend fun <T> executeParallel(
        tasks: List<suspend () -> T>
    ): List<T> = coroutineScope {
        tasks.map { async { it() } }.awaitAll()
    }

    /**
     * Delegate work to another agent.
     */
    suspend fun delegateTo(
        targetAgent: Agent,
        request: AgentRequest
    ): AgentResult {
        return executeAction(
            action = StepType.DECIDE,
            target = "Delegate to ${targetAgent.identity()}: ${request.action}"
        ) {
            targetAgent.execute(request)
        }
    }

    /**
     * Primary entry point to execute an AgentRequest.
     */
    override suspend fun execute(request: AgentRequest): AgentResult {
        if (isQuarantined) {
            return AgentResult(
                requestId = request.requestId,
                status = AgentStatus.FAILED,
                error = "Agent $name is quarantined by ImmuneSystem due to high anomaly/failure rates."
            )
        }

        val startTime = System.currentTimeMillis()
        currentStatus = AgentStatus.EXECUTING
        val token = CancellationToken()
        activeToken = token

        return coroutineScope {
            activeJob = coroutineContext[Job]
            try {
                val result = onExecute(request, token)
                lastResult = result
                currentStatus = result.status
                val duration = System.currentTimeMillis() - startTime
                if (result.isSuccess) {
                    ScorecardRegistry.global.recordSuccess(name, duration)
                } else {
                    ScorecardRegistry.global.recordFailure(name, duration, isTimeout = false, isCancel = false)
                }
                // Metabolic recovery upon successful run
                metabolicStress = (metabolicStress - 0.03).coerceAtLeast(0.0)
                result
            } catch (e: CancelledException) {
                currentStatus = AgentStatus.CANCELLED
                val duration = System.currentTimeMillis() - startTime
                ScorecardRegistry.global.recordFailure(name, duration, isTimeout = false, isCancel = true)
                val res = AgentResult(
                    requestId = request.requestId,
                    status = AgentStatus.CANCELLED,
                    error = e.message,
                    durationMs = duration
                )
                lastResult = res
                res
            } catch (e: Exception) {
                currentStatus = AgentStatus.FAILED
                failureCount++
                val duration = System.currentTimeMillis() - startTime
                ScorecardRegistry.global.recordFailure(name, duration, isTimeout = false, isCancel = false)
                val res = AgentResult(
                    requestId = request.requestId,
                    status = AgentStatus.FAILED,
                    error = e.message ?: e.toString(),
                    durationMs = duration
                )
                lastResult = res
                res
            } finally {
                activeToken = null
                activeJob = null
                if (currentStatus == AgentStatus.EXECUTING) {
                    currentStatus = AgentStatus.IDLE
                }
            }
        }
    }

    /**
     * Subclasses implement domain-specific agent reasoning & action.
     */
    protected abstract suspend fun onExecute(request: AgentRequest, token: CancellationToken): AgentResult

    fun quarantine(reason: String) {
        isQuarantined = true
        currentStatus = AgentStatus.FAILED
        logger.logInstant(
            agentName = name,
            action = StepType.ERROR,
            target = "Agent quarantined: $reason",
            status = StepStatus.FAILED
        )
    }

    fun liftQuarantine() {
        isQuarantined = false
        failureCount = 0
        metabolicStress = 0.0
        currentStatus = AgentStatus.IDLE
    }
}
