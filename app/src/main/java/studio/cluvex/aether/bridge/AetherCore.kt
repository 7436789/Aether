package studio.cluvex.aether.bridge

import android.util.Log
import org.json.JSONObject

/**
 * Direct bridge to the Rust Aether core through its stable C FFI.
 *
 * Every call returns the core's JSON reply as a plain string; this object
 * also parses the common envelope (`{"ok": true, ...}` / `{"ok": false,
 * "error": "..."}`) so callers get typed results.
 */
object AetherCore {

    private const val TAG = "AetherCore"

    @Volatile
    private var initialized = false

    /** Version of the bundled core, cached from [nativeVersion]. */
    @Volatile
    var coreVersion: String = "?"
        private set

    /**
     * Loads libaether.so (the Rust cdylib) plus the small C shim that
     * adapts its `char*` FFI to JNI, then resolves the core symbols.
     *
     * @param libDir the `nativeLibraryDir` of the application, used to
     *        dlopen the core by absolute path.
     */
    fun initialize(libDir: String) {
        if (initialized) {
            return
        }
        synchronized(this) {
            if (initialized) {
                return
            }
            System.loadLibrary("aether")
            System.loadLibrary("aether_jni")
            check(nativeInit(libDir)) { "libaether.so could not be loaded from $libDir" }
            initialized = true
            runCatching {
                coreVersion = JSONObject(nativeVersion()).optString("version", "?")
            }
            Log.i(TAG, "aether core ready, version $coreVersion")
        }
    }

    // -- typed helpers ------------------------------------------------------

    /**
     * Result of a `*_start` call: either a job handle to poll, or an error
     * message when the core refused the request outright.
     */
    data class JobHandle(val jobId: Long)

    fun coreStart(arguments: List<String>): JobHandle {
        val reply = JSONObject(nativeCoreStart(arguments.joinToString(",", "[", "]") { arg ->
            JSONObject.quote(arg)
        }))
        if (!reply.optBoolean("ok", false)) {
            throw CoreException(reply.optString("error", "the core refused to start"))
        }
        val id = reply.optLong("job", -1L)
        if (id <= 0) {
            throw CoreException("the core did not hand out a job handle")
        }
        return JobHandle(id)
    }

    /** Snapshot of an asynchronous job on the core. */
    sealed interface JobState {
        /** Still running. */
        data object Running : JobState

        /** Finished; [result] is the inner JSON reply of the job. */
        data class Done(val result: CoreReply) : JobState
    }

    data class CoreReply(
        val ok: Boolean,
        val state: String?,
        val error: String?,
    )

    fun jobPoll(jobId: Long): JobState {
        val reply = JSONObject(nativeJobPoll(jobId))
        if (!reply.optBoolean("ok", false)) {
            return JobState.Done(CoreReply(ok = false, state = null, error = reply.optString("error")))
        }
        return when (reply.optString("state")) {
            "running" -> JobState.Running
            else -> {
                val inner = reply.optJSONObject("result") ?: JSONObject()
                JobState.Done(
                    CoreReply(
                        ok = inner.optBoolean("ok", false),
                        state = inner.optString("state"),
                        error = if (inner.has("error")) inner.optString("error") else null,
                    ),
                )
            }
        }
    }

    fun jobCancel(jobId: Long) {
        runCatching { nativeJobCancel(jobId) }
    }

    fun jobFree(jobId: Long) {
        runCatching { nativeJobFree(jobId) }
    }

    fun setEnv(name: String, value: String) {
        nativeSetEnv(name, value)
    }

    class CoreException(message: String) : RuntimeException(message)

    // -- native surface -----------------------------------------------------

    private external fun nativeInit(libDir: String): Boolean
    private external fun nativeVersion(): String
    private external fun nativeCoreStart(argsJson: String): String
    private external fun nativeJobPoll(jobId: Long): String
    private external fun nativeJobCancel(jobId: Long): String
    private external fun nativeJobFree(jobId: Long): String
    private external fun nativeSetEnv(name: String, value: String)
    external fun nativeStartStderrCapture(): Boolean
    external fun nativeReadStderr(buffer: ByteArray): Int
    external fun nativeStopStderrCapture()
}
