#include <jni.h>

#include <atomic>
#include <cstdint>
#include <cstdlib>

#include <pthread.h>
#include <signal.h>
#include <sys/syscall.h>
#include <unistd.h>

#include "NativeApkAttestation.h"

namespace {
std::atomic<uint64_t> g_stamp{0};
std::atomic<uint32_t> g_ticket{0};
std::atomic<bool> g_armed{false};

uint32_t mix32(uint32_t value) {
    value ^= value >> 16u;
    value *= 0x7feb352du;
    value ^= value >> 15u;
    value *= 0x846ca68bu;
    value ^= value >> 16u;
    return value;
}

uint32_t make_ticket(int seed, uint64_t stamp) {
    const uint32_t pid = static_cast<uint32_t>(getpid());
    const uint32_t lo = static_cast<uint32_t>(stamp);
    const uint32_t hi = static_cast<uint32_t>(stamp >> 32u);
    return mix32(static_cast<uint32_t>(seed) ^ pid ^ lo ^ (hi * 0x9e3779b9u) ^ 0x51ed270bu);
}

[[noreturn]] void terminate_now() {
    const pid_t pid = getpid();
    const pid_t tid = static_cast<pid_t>(syscall(SYS_gettid));
    syscall(SYS_tgkill, pid, tid, SIGKILL);
    kill(pid, SIGKILL);
    _exit(137);
}

void *native_reaper(void *) {
    usleep(420000u);
    if (g_armed.load(std::memory_order_acquire)) {
        terminate_now();
    }
    return nullptr;
}

bool start_reaper() {
    pthread_t thread{};
    if (pthread_create(&thread, nullptr, native_reaper, nullptr) != 0) {
        return false;
    }
    pthread_detach(thread);
    return true;
}
} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_onecore_loader_security_NativeLicenseGuard_nativePulseA(
        JNIEnv *, jclass, jint seed, jlong stamp) {
    if (stamp == 0) {
        terminate_now();
    }

    // A terminal security decision should not depend on potentially patched native text.
    if (!onecore_verify_native_text_integrity()) {
        terminate_now();
    }

    const uint64_t stamp_value = static_cast<uint64_t>(stamp);
    const uint32_t ticket = make_ticket(seed, stamp_value);
    g_stamp.store(stamp_value, std::memory_order_release);
    g_ticket.store(ticket, std::memory_order_release);
    g_armed.store(true, std::memory_order_release);

    if (!start_reaper()) {
        terminate_now();
    }
    return static_cast<jint>(ticket);
}

extern "C" JNIEXPORT void JNICALL
Java_com_onecore_loader_security_NativeLicenseGuard_nativePulseB(
        JNIEnv *, jclass, jint ticket, jlong stamp) {
    const uint64_t stamp_value = static_cast<uint64_t>(stamp);
    const uint32_t ticket_value = static_cast<uint32_t>(ticket);
    const bool armed = g_armed.load(std::memory_order_acquire);
    const bool matched = armed
            && g_stamp.load(std::memory_order_acquire) == stamp_value
            && g_ticket.load(std::memory_order_acquire) == ticket_value;

    // Once pulse A arms a terminal decision, even a corrupted second-stage ticket remains
    // fail-closed because the detached reaper will terminate shortly. A correct ticket commits now.
    if (matched) {
        terminate_now();
    }
}
