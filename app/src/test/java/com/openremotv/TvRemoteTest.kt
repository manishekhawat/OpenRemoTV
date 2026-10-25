package com.openremotv

import com.openremotv.tv.Proto
import com.openremotv.tv.TvRemote
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

/** The hand-written protobuf and the pairing secret's byte forms, checked against known encodings. */
class TvRemoteTest {

    @Test fun varints() {
        assertArrayEquals(byteArrayOf(0x01), Proto.varintBytes(1))
        assertArrayEquals(byteArrayOf(0xAC.toByte(), 0x02), Proto.varintBytes(300))
        assertArrayEquals(byteArrayOf(0xC8.toByte(), 0x01), Proto.varintBytes(200))
    }

    /** An outer pairing message: version 2, status 200, pairing_request{service "a"}. */
    @Test fun messageRoundTrip() {
        val msg = Proto().varint(1, 2).varint(2, 200).message(10, Proto().string(1, "a"))
        val bytes = msg.toByteArray()
        assertArrayEquals(byteArrayOf(0x08, 0x02, 0x10, 0xC8.toByte(), 0x01, 0x52, 0x03, 0x0A, 0x01, 0x61), bytes)
        val back = Proto.read(bytes)
        assertEquals(2, back.int(1))
        assertEquals(200, back.int(2))
        assertTrue(back.has(10))
        assertEquals("a", String(Proto.read(back.bytes(10)).bytes(1)))
    }

    /** The exponent 65537 is hashed as "0" + "10001" → 01 00 01, as the TV's pairing expects. */
    @Test fun secretByteForms() {
        assertArrayEquals(byteArrayOf(0x01, 0x00, 0x01), TvRemote.hexBytes("0" + BigInteger.valueOf(65537).toString(16)))
        assertArrayEquals(byteArrayOf(0x12, 0xAB.toByte()), TvRemote.hexBytes("12ab"))
        assertArrayEquals(byteArrayOf(0x80.toByte()), TvRemote.magnitude(BigInteger.valueOf(128)))
    }
}
