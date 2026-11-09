// CPU affinity for the calling thread, used to keep LiteRT inference on cores of one
// microarchitecture. XNNPACK and ruy pick a kernel per core type, and those kernels round
// differently, so the same input can give slightly different outputs on big vs LITTLE cores.
#define _GNU_SOURCE
#include <jni.h>
#include <sched.h>
#include <stdint.h>

#define MAX_MASK_CPUS 64

// Returns the calling thread's affinity as a bitmask of CPUs 0..63, or 0 on failure.
JNIEXPORT jlong JNICALL
Java_in_c1ph3rj_scanly_core_ml_CpuAffinity_nativeGetAffinity(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    cpu_set_t set;
    CPU_ZERO(&set);
    if (sched_getaffinity(0, sizeof(set), &set) != 0) return 0;
    // Build the mask unsigned: shifting a signed 1 into bit 63 is undefined behaviour.
    uint64_t mask = 0;
    for (int cpu = 0; cpu < MAX_MASK_CPUS; cpu++) {
        if (CPU_ISSET(cpu, &set)) mask |= (uint64_t) 1 << cpu;
    }
    return (jlong) mask;
}

// Restricts the calling thread to the CPUs in mask. Returns 0 on success, else -1.
JNIEXPORT jint JNICALL
Java_in_c1ph3rj_scanly_core_ml_CpuAffinity_nativeSetAffinity(JNIEnv *env, jclass clazz, jlong mask) {
    (void) env;
    (void) clazz;
    cpu_set_t set;
    CPU_ZERO(&set);
    for (int cpu = 0; cpu < MAX_MASK_CPUS; cpu++) {
        if ((uint64_t) mask & ((uint64_t) 1 << cpu)) CPU_SET(cpu, &set);
    }
    return sched_setaffinity(0, sizeof(set), &set) == 0 ? 0 : -1;
}

// CPU the calling thread is running on, or -1.
JNIEXPORT jint JNICALL
Java_in_c1ph3rj_scanly_core_ml_CpuAffinity_nativeCurrentCpu(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    return sched_getcpu();
}
