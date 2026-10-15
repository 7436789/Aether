// ---------------------------------------------------------------------------
// aether_jni.cpp — thin JNI shim between Kotlin and the Rust Aether core.
//
// The core is a Rust cdylib (libaether.so) exposing a stable C FFI with
// JSON-in/JSON-out semantics (see core/aether/src/ffi.rs):
//
//   char* aether_version();
//   char* aether_core_start(const char* arguments_json);
//   char* aether_job_poll(uint64_t id);
//   char* aether_job_cancel(uint64_t id);
//   char* aether_job_free(uint64_t id);
//   void  aether_string_free(char* ptr);
//
// Every call that returns char* hands over ownership of a heap C string that
// must be released with aether_string_free. This shim converts the pointers
// to Java strings, frees them immediately, and adds:
//
//   * nativeSetEnv      — process environment for AETHER_* variables
//   * stderr capture    — dup2 a pipe over fd 2 so Rust env_logger output,
//                         arti tracing and Go transport logs can be shown
//                         inside the app
// ---------------------------------------------------------------------------

#include <jni.h>
#include <dlfcn.h>
#include <unistd.h>
#include <fcntl.h>
#include <cerrno>
#include <cstring>
#include <cstdlib>
#include <string>

#include <android/log.h>

#define TAG "aether-jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// --- Rust FFI signatures ---------------------------------------------------

typedef char* (*fn_version)();
typedef char* (*fn_core_start)(const char*);
typedef char* (*fn_job_poll)(unsigned long long);
typedef char* (*fn_job_cancel)(unsigned long long);
typedef char* (*fn_job_free)(unsigned long long);
typedef void (*fn_string_free)(char*);

static fn_version s_version = nullptr;
static fn_core_start s_core_start = nullptr;
static fn_job_poll s_job_poll = nullptr;
static fn_job_cancel s_job_cancel = nullptr;
static fn_job_free s_job_free = nullptr;
static fn_string_free s_string_free = nullptr;

// --- stderr capture state --------------------------------------------------

static int s_stderr_read_fd = -1;
static int s_stderr_saved_fd = -1;

// --- helpers ---------------------------------------------------------------

static jstring reply_to_jstring(JNIEnv* env, char* reply) {
    if (s_string_free == nullptr) {
        return env->NewStringUTF("{\"ok\":false,\"error\":\"the core is not loaded\"}");
    }
    if (reply == nullptr) {
        return env->NewStringUTF("{\"ok\":false,\"error\":\"the core returned null\"}");
    }
    jstring out = env->NewStringUTF(reply);
    s_string_free(reply);
    return (out == nullptr)
        ? env->NewStringUTF("{\"ok\":false,\"error\":\"the reply was not valid utf-8\"}")
        : out;
}

static const char* json_string(JNIEnv* env, jstring value) {
    return (value == nullptr) ? nullptr : env->GetStringUTFChars(value, nullptr);
}

static void release_string(JNIEnv* env, jstring value, const char* raw) {
    if (value != nullptr && raw != nullptr) {
        env->ReleaseStringUTFChars(value, raw);
    }
}

static std::string library_path(const char* dir, const char* name) {
    std::string path(dir);
    if (!path.empty() && path.back() != '/') {
        path += '/';
    }
    path += name;
    return path;
}

// --- initialization --------------------------------------------------------

extern "C" JNIEXPORT jboolean JNICALL
Java_studio_cluvex_aether_bridge_AetherCore_nativeInit(JNIEnv* env, jobject /*thiz*/,
                                                       jstring lib_dir) {
    static bool resolved = false;
    if (resolved) {
        return JNI_TRUE;
    }

    const char* dir = (lib_dir == nullptr) ? "" : env->GetStringUTFChars(lib_dir, nullptr);
    std::string path = library_path(dir, "libaether.so");
    if (lib_dir != nullptr) {
        env->ReleaseStringUTFChars(lib_dir, dir);
    }

    void* handle = dlopen(path.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (handle == nullptr) {
        // Fallback to the bare soname for cases where the core was already
        // loaded by the class loader under that name.
        handle = dlopen("libaether.so", RTLD_NOW | RTLD_GLOBAL);
    }
    if (handle == nullptr) {
        LOGE("dlopen failed: %s", dlerror());
        return JNI_FALSE;
    }

    s_version = reinterpret_cast<fn_version>(dlsym(handle, "aether_version"));
    s_core_start = reinterpret_cast<fn_core_start>(dlsym(handle, "aether_core_start"));
    s_job_poll = reinterpret_cast<fn_job_poll>(dlsym(handle, "aether_job_poll"));
    s_job_cancel = reinterpret_cast<fn_job_cancel>(dlsym(handle, "aether_job_cancel"));
    s_job_free = reinterpret_cast<fn_job_free>(dlsym(handle, "aether_job_free"));
    s_string_free = reinterpret_cast<fn_string_free>(dlsym(handle, "aether_string_free"));

    if (s_version == nullptr || s_core_start == nullptr || s_job_poll == nullptr ||
        s_job_cancel == nullptr || s_job_free == nullptr || s_string_free == nullptr) {
        LOGE("dlsym failed, the core FFI is incomplete");
        s_version = nullptr;
        s_core_start = nullptr;
        s_job_poll = nullptr;
        s_job_cancel = nullptr;
        s_job_free = nullptr;
        s_string_free = nullptr;
        return JNI_FALSE;
    }

    resolved = true;
    LOGI("aether core loaded from %s", path.c_str());
    return JNI_TRUE;
}

// --- core FFI --------------------------------------------------------------

extern "C" JNIEXPORT jstring JNICALL
Java_studio_cluvex_aether_bridge_AetherCore_nativeVersion(JNIEnv* env, jobject) {
    if (s_version == nullptr) {
        return env->NewStringUTF("{\"ok\":false,\"error\":\"the core is not loaded\"}");
    }
    return reply_to_jstring(env, s_version());
}

extern "C" JNIEXPORT jstring JNICALL
Java_studio_cluvex_aether_bridge_AetherCore_nativeCoreStart(JNIEnv* env, jobject,
                                                            jstring args_json) {
    if (s_core_start == nullptr) {
        return env->NewStringUTF("{\"ok\":false,\"error\":\"the core is not loaded\"}");
    }
    const char* raw = json_string(env, args_json);
    if (raw == nullptr) {
        return env->NewStringUTF("{\"ok\":false,\"error\":\"arguments were null\"}");
    }
    char* reply = s_core_start(raw);
    release_string(env, args_json, raw);
    return reply_to_jstring(env, reply);
}

extern "C" JNIEXPORT jstring JNICALL
Java_studio_cluvex_aether_bridge_AetherCore_nativeJobPoll(JNIEnv* env, jobject, jlong job_id) {
    if (s_job_poll == nullptr) {
        return env->NewStringUTF("{\"ok\":false,\"error\":\"the core is not loaded\"}");
    }
    return reply_to_jstring(env, s_job_poll(static_cast<unsigned long long>(job_id)));
}

extern "C" JNIEXPORT jstring JNICALL
Java_studio_cluvex_aether_bridge_AetherCore_nativeJobCancel(JNIEnv* env, jobject, jlong job_id) {
    if (s_job_cancel == nullptr) {
        return env->NewStringUTF("{\"ok\":false,\"error\":\"the core is not loaded\"}");
    }
    return reply_to_jstring(env, s_job_cancel(static_cast<unsigned long long>(job_id)));
}

extern "C" JNIEXPORT jstring JNICALL
Java_studio_cluvex_aether_bridge_AetherCore_nativeJobFree(JNIEnv* env, jobject, jlong job_id) {
    if (s_job_free == nullptr) {
        return env->NewStringUTF("{\"ok\":false,\"error\":\"the core is not loaded\"}");
    }
    return reply_to_jstring(env, s_job_free(static_cast<unsigned long long>(job_id)));
}

// --- environment -----------------------------------------------------------

extern "C" JNIEXPORT void JNICALL
Java_studio_cluvex_aether_bridge_AetherCore_nativeSetEnv(JNIEnv* env, jobject,
                                                         jstring name, jstring value) {
    const char* raw_name = json_string(env, name);
    const char* raw_value = json_string(env, value);
    if (raw_name != nullptr && raw_value != nullptr) {
        setenv(raw_name, raw_value, 1);
    }
    release_string(env, name, raw_name);
    release_string(env, value, raw_value);
}

// --- stderr capture --------------------------------------------------------

extern "C" JNIEXPORT jboolean JNICALL
Java_studio_cluvex_aether_bridge_AetherCore_nativeStartStderrCapture(JNIEnv* /*env*/, jobject) {
    if (s_stderr_read_fd != -1) {
        return JNI_TRUE; // already capturing
    }

    int fds[2];
    if (pipe2(fds, O_NONBLOCK) != 0) {
        LOGE("pipe2 failed: %s", strerror(errno));
        return JNI_FALSE;
    }

    s_stderr_saved_fd = dup(STDERR_FILENO);
    if (s_stderr_saved_fd == -1) {
        close(fds[0]);
        close(fds[1]);
        LOGE("dup failed: %s", strerror(errno));
        return JNI_FALSE;
    }

    fflush(stderr);
    if (dup2(fds[1], STDERR_FILENO) == -1) {
        close(fds[0]);
        close(fds[1]);
        close(s_stderr_saved_fd);
        s_stderr_saved_fd = -1;
        LOGE("dup2 failed: %s", strerror(errno));
        return JNI_FALSE;
    }
    close(fds[1]); // fd 2 is now the sole reference to the write end

    s_stderr_read_fd = fds[0];
    LOGI("stderr capture started (read fd %d)", s_stderr_read_fd);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jint JNICALL
Java_studio_cluvex_aether_bridge_AetherCore_nativeReadStderr(JNIEnv* env, jobject,
                                                             jbyteArray buffer) {
    if (s_stderr_read_fd == -1 || buffer == nullptr) {
        return -1;
    }

    jsize capacity = env->GetArrayLength(buffer);
    if (capacity <= 0) {
        return -1;
    }

    jbyte stack[4096];
    jbyte* chunk = (capacity <= static_cast<jsize>(sizeof(stack)))
        ? stack
        : env->GetByteArrayElements(buffer, nullptr);
    if (chunk == nullptr) {
        return -1;
    }

    ssize_t got = read(s_stderr_read_fd, chunk, static_cast<size_t>(capacity));

    if (chunk != stack) {
        if (got > 0) {
            env->ReleaseByteArrayElements(buffer, chunk, JNI_OK);
        } else {
            env->ReleaseByteArrayElements(buffer, chunk, JNI_ABORT);
        }
    } else if (got > 0) {
        env->SetByteArrayRegion(buffer, 0, static_cast<jsize>(got), chunk);
    }

    if (got > 0) {
        return static_cast<jint>(got);
    }
    if (got == 0 || errno == EAGAIN || errno == EWOULDBLOCK) {
        return 0; // nothing waiting right now
    }
    return -1;
}

extern "C" JNIEXPORT void JNICALL
Java_studio_cluvex_aether_bridge_AetherCore_nativeStopStderrCapture(JNIEnv* /*env*/, jobject) {
    if (s_stderr_read_fd == -1) {
        return;
    }
    close(s_stderr_read_fd);
    s_stderr_read_fd = -1;

    if (s_stderr_saved_fd != -1) {
        fflush(stderr);
        dup2(s_stderr_saved_fd, STDERR_FILENO);
        close(s_stderr_saved_fd);
        s_stderr_saved_fd = -1;
    }
    LOGI("stderr capture stopped");
}
