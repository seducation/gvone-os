package com.example.data.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.agent.cns.CentralNervousSystem
import com.example.data.agent.BotAssistantJob
import com.example.data.agent.BotAssistantRuntime
import com.example.data.ai.GVONEAIService
import com.example.data.environment.EnvironmentManager
import com.example.data.tor.TorManager
import com.example.ui.screens.ide.GrokBotPersona
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** User-started assistant worker; it continues queued CNS missions while GVONE is minimized. */
class BotAssistantService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: Job? = null
    private lateinit var runtime: BotAssistantRuntime
    private lateinit var aiService: GVONEAIService

    override fun onCreate() {
        super.onCreate()
        runtime = BotAssistantRuntime.get(applicationContext)
        aiService = GVONEAIService(TorManager(), application)
        CentralNervousSystem.global.initializeWithDefaults(
            context = applicationContext,
            viewModel = Any(),
            repository = Any(),
            aiService = aiService,
            environmentManager = EnvironmentManager(applicationContext)
        )
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == BotAssistantRuntime.ACTION_STOP) {
            runtime.stopFromService()
            worker?.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        when (action) {
            BotAssistantRuntime.ACTION_PAUSE -> runtime.setPausedFromService(true)
            BotAssistantRuntime.ACTION_RESUME -> runtime.setPausedFromService(false)
        }
        ensureWorker()
        return START_STICKY
    }

    private fun ensureWorker() {
        if (worker?.isActive == true) {
            runtime.notifyWorkAvailable()
            return
        }
        worker = serviceScope.launch {
            while (true) {
                if (!runtime.enabled.value || runtime.paused.value) {
                    runtime.setServiceStopped()
                    updateNotification()
                    runtime.awaitWorkSignal()
                    continue
                }

                val job = runtime.nextJobOrNull()
                if (job == null) {
                    runtime.setServiceStopped()
                    updateNotification()
                    runtime.awaitWorkSignal()
                    continue
                }

                process(job)
            }
        }
    }

    private suspend fun process(job: BotAssistantJob) {
        val persona = GrokBotPersona.values().firstOrNull { it.id == job.personaId }
            ?: GrokBotPersona.GROK_CODER
        runtime.setWorking(job.prompt.take(90))
        updateNotification()

        try {
            val goal = if (job.isContextReview) {
                val proposal = aiService.askAI(
                    """${persona.systemRole}
                        You are reviewing a context change inside GVONE while the user has enabled its autonomous assistant.
                        Decide whether one useful, bounded task can be performed from the context below. Return exactly NO_ACTION if there is no clear task. Otherwise return one concise task goal only. Never request access to other apps or device data.

                        Context:
                        ${job.context.take(MAX_CONTEXT_CHARS)}
                    """.trimIndent()
                )?.trim().orEmpty()
                if (proposal.isBlank() || proposal.trimStart().startsWith("NO_ACTION", ignoreCase = true)) return
                proposal
            } else {
                job.prompt
            }

            val mission = """${persona.systemRole}
                GVONE assistant task:
                $goal

                Current GVONE context:
                ${job.context.take(MAX_CONTEXT_CHARS)}
            """.trimIndent()
            val result = CentralNervousSystem.global.orchestrateGoal(mission)
            val response = result.synthesis.ifBlank {
                "Task ${if (result.success) "completed" else "finished"}. See the CNS dashboard for execution details."
            }
            runtime.addAssistantResponse(response, persona.id, extractCode(response))
            if (job.echoToTerminal) {
                runtime.publishTerminalOutput(response, if (result.success) com.example.data.terminal.TerminalLineType.SUCCESS else com.example.data.terminal.TerminalLineType.WARNING)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val message = "Agent task failed: ${error.message ?: "Unknown error"}"
            runtime.reportError(message)
            if (job.echoToTerminal) {
                runtime.publishTerminalOutput(message, com.example.data.terminal.TerminalLineType.ERROR)
            }
        } finally {
            runtime.setWorking(null)
            updateNotification()
        }
    }

    private fun extractCode(text: String): String? {
        if (!text.contains("```")) return null
        return text.substringAfter("```").substringAfter('\n').substringBefore("```").takeIf { it.isNotBlank() }
    }

    private fun buildNotification(): Notification {
        val paused = runtime.paused.value
        val working = runtime.activeTask.value
        val pauseIntent = actionIntent(
            if (paused) BotAssistantRuntime.ACTION_RESUME else BotAssistantRuntime.ACTION_PAUSE,
            if (paused) REQUEST_RESUME else REQUEST_PAUSE
        )
        val stopIntent = actionIntent(BotAssistantRuntime.ACTION_STOP, REQUEST_STOP)
        val openIntent = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (paused) "GVONE Agent paused" else "GVONE Agent active")
            .setContentText(working ?: "Ready for GVONE tasks")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_launcher_foreground, if (paused) "Resume" else "Pause", pauseIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopIntent)
            .build()
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, BotAssistantService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GVONE Agent",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status and controls for the GVONE autonomous assistant"
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        worker?.cancel()
        serviceScope.cancel()
        runtime.setServiceStopped()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "gvone_bot_agent"
        private const val NOTIFICATION_ID = 4107
        private const val REQUEST_OPEN = 4108
        private const val REQUEST_PAUSE = 4109
        private const val REQUEST_RESUME = 4110
        private const val REQUEST_STOP = 4111
        private const val MAX_CONTEXT_CHARS = 12000
    }
}
