// Drives the RGB LED bar of the ALLNET MeetTwo through the vendor kernel driver.
//
// The driver exposes /dev/ledjni. One ioctl per channel sets its level:
// request 161 = red, 162 = green, 163 = blue, argument = level 0..15.
// This matches the vendor library libjnielc.so (seekstart = open, ledseek = ioctl,
// seekstop = close) used by the preinstalled app com.elc.smt_test.
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <sys/ioctl.h>
#include <unistd.h>

#define LED_DEVICE "/dev/ledjni"
#define LED_RED 161
#define LED_GREEN 162
#define LED_BLUE 163
#define LED_MAX_LEVEL 15

// The driver (kernel module wa_two_leds) uses the level as a table index without a bounds
// check: a level above 15 causes a kernel panic and the tablet reboots. Never pass it on.
static unsigned long clamp_level(jint level) {
    if (level < 0) return 0;
    if (level > LED_MAX_LEVEL) return LED_MAX_LEVEL;
    return (unsigned long) level;
}

// Returns 0 on success or a negative errno value.
JNIEXPORT jint JNICALL
Java_org_roomscreen_meettwo_hardware_NativeLedDevice_nativeWrite(
        JNIEnv *env, jobject thiz, jint red, jint green, jint blue) {
    (void) env;
    (void) thiz;
    int fd = open(LED_DEVICE, O_RDONLY | O_NOCTTY | O_CLOEXEC);
    if (fd < 0) {
        return -errno;
    }
    int result = 0;
    if (ioctl(fd, LED_RED, clamp_level(red)) < 0 ||
        ioctl(fd, LED_GREEN, clamp_level(green)) < 0 ||
        ioctl(fd, LED_BLUE, clamp_level(blue)) < 0) {
        result = -errno;
    }
    close(fd);
    return result;
}
