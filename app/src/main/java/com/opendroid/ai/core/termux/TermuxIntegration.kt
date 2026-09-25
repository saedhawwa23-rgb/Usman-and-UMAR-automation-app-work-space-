package com.opendroid.ai.core.termux

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Supported, user-initiated local tasks. Arbitrary command text is intentionally not accepted. */
enum class TermuxTask(val label: String, val command: String) {
    CHECK_HOME("Check Termux home", "pwd"),
    LIST_FILES("List Termux files", "ls -la"),
    SYSTEM_INFO("Read Termux system info", "uname -a")
}

data class TermuxResult(
    val task: TermuxTask,
    val stdout: String,
    val stderr: String,
    val exitCode: Int
)

@Singleton
class TermuxIntegration @Inject constructor(
    @ApplicationContext
    private val context: Context
) {
    companion object {
        const val TERMUX_PACKAGE = "com.termux"
        private const val RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
        private const val RESULT_ACTION = "com.usmanandumar.app.TERMUX_RESULT"
        private const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
        private const val REQUEST_CODE = 4242
    }

    private val _results = MutableStateFlow<TermuxResult?>(null)
    val results: StateFlow<TermuxResult?> = _results.asStateFlow()
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val taskName = intent.getStringExtra("task") ?: return
            val task = runCatching { TermuxTask.valueOf(taskName) }.getOrNull() ?: return
            _results.value = TermuxResult(
                task = task,
                stdout = intent.getStringExtra("stdout").orEmpty(),
                stderr = intent.getStringExtra("stderr").orEmpty(),
                exitCode = intent.getIntExtra("exitCode", -1)
            )
        }
    }

    init {
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(RESULT_ACTION),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    fun isInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        true
    }.getOrDefault(false)

    fun run(task: TermuxTask): Result<Unit> = runCatching {
        check(isInstalled()) { "Termux is not installed." }
        val callback = Intent(RESULT_ACTION).setPackage(context.packageName)
            .putExtra("task", task.name)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            callback,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val command = Intent(RUN_COMMAND_ACTION).apply {
            setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/sh")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", task.command))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
            putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pendingIntent)
        }
        context.startService(command)
    }

    fun close() {
        runCatching { context.unregisterReceiver(receiver) }
    }
}
