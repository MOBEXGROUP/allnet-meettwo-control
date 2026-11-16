package org.roomscreen.meettwo.card

/**
 * Extracts the card UID from an NCI `RF_INTF_ACTIVATED_NTF` (`61 05 …`), the message the
 * NFC controller sends when a card is activated (NCI 2.0, chapter 7.3).
 *
 * Payload: discovery ID, RF interface, RF protocol, activation technology and mode, max
 * payload size, initial credits, length of the technology-specific parameters, parameters.
 * The UID is returned in the order Android's `Tag.getId()` and the firmware's NFC
 * keyboard input use, as upper-case hex.
 */
object NciActivation {
    private const val MODE_POLL_A = 0x00
    private const val MODE_POLL_B = 0x01
    private const val MODE_POLL_F = 0x02
    private const val MODE_POLL_V = 0x06

    fun uidFromHex(hex: String): String? {
        val bytes = hex.trim().takeIf { it.length % 2 == 0 && it.isNotEmpty() }
            ?.chunked(2)?.map { it.toIntOrNull(16) ?: return null } ?: return null
        return uid(bytes)
    }

    private fun uid(b: List<Int>): String? {
        if (b.size < 10 || b[0] != 0x61 || b[1] != 0x05) return null
        val payload = 3
        val mode = b[payload + 3]
        val paramsLength = b[payload + 6]
        val paramsStart = payload + 7
        if (b.size < paramsStart + paramsLength) return null
        val params = b.subList(paramsStart, paramsStart + paramsLength)
        val uid = when (mode) {
            // SENS_RES (2), NFCID1 length (1), NFCID1, SEL_RES length, SEL_RES
            MODE_POLL_A -> params.slice(3, params.getOrNull(2))
            // SENSB_RES length (1), SENSB_RES without the leading 0x50: NFCID0 = first 4 bytes
            MODE_POLL_B -> params.slice(1, 4)
            // bit rate (1), SENSF_RES length (1), SENSF_RES: response code (1), NFCID2 (8)
            MODE_POLL_F -> params.slice(3, 8)
            // RES_FLAG (1), DSFID (1), UID (8)
            MODE_POLL_V -> params.slice(2, 8)
            else -> null
        }
        return uid?.takeIf { it.isNotEmpty() }?.joinToString("") { "%02X".format(it) }
    }

    private fun List<Int>.slice(start: Int, length: Int?): List<Int>? {
        if (length == null || length <= 0 || start + length > size) return null
        return subList(start, start + length)
    }
}
