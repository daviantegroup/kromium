package dev.daviante.kromium.presentation.handler

import java.util.Collections
import java.util.EnumSet

/**
 * Types of media and hardware device permissions requested by web applications.
 *
 * @property flag Bitmask value corresponding to Chromium's native media permission flags.
 */
enum class KromiumPermissionType(val flag: Int) {
    /** Microphone / audio input device access (`DEVICE_AUDIO_CAPTURE`). */
    AUDIO_CAPTURE(1),

    /** Camera / webcam video input device access (`DEVICE_VIDEO_CAPTURE`). */
    VIDEO_CAPTURE(2),

    /** System / desktop audio capture access (`DESKTOP_AUDIO_CAPTURE`). */
    DESKTOP_AUDIO(4),

    /** Screen sharing / desktop video capture access (`DESKTOP_VIDEO_CAPTURE`). */
    DESKTOP_VIDEO(8);

    companion object {
        /**
         * Decodes a native Chromium media permission bitmask into a set of [KromiumPermissionType].
         */
        @JvmStatic
        fun fromFlags(flags: Int): Set<KromiumPermissionType> {
            val result = EnumSet.noneOf(KromiumPermissionType::class.java)
            for (type in entries) {
                if ((flags and type.flag) != 0) {
                    result.add(type)
                }
            }
            return Collections.unmodifiableSet(result)
        }

        /**
         * Encodes a collection of [KromiumPermissionType] into a native Chromium bitmask.
         */
        @JvmStatic
        fun toFlags(types: Iterable<KromiumPermissionType>): Int {
            var mask = 0
            for (type in types) {
                mask = mask or type.flag
            }
            return mask
        }
    }
}
