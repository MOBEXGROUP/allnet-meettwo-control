package org.roomscreen.meettwo.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NciActivationTest {
    @Test
    fun parsesNfcAUidFromRealTablet() {
        // Captured on the MeetTwo (NxpNciR) with a DESFire card; the UID is replaced by an example.
        val ntf = "61051E01020400FF010D44030704A1B2C3D4E5F68001200000000006057577810280"
        assertEquals("04A1B2C3D4E5F6", NciActivation.uidFromHex(ntf))
    }

    @Test
    fun parsesFourByteNfcAUid() {
        val ntf = "61051301020400FF010A0400" + "04" + "A1B2C3D4" + "0108" + "00000000"
        assertEquals("A1B2C3D4", NciActivation.uidFromHex(ntf))
    }

    @Test
    fun ignoresOtherMessages() {
        assertNull(NciActivation.uidFromHex("6003020100"))
        assertNull(NciActivation.uidFromHex("4000"))
        assertNull(NciActivation.uidFromHex("zz"))
    }
}
