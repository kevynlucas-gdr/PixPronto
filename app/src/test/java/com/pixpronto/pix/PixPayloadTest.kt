package com.pixpronto.pix

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class PixPayloadTest {
    @Test fun crc16CcittKnownVector() { assertEquals("29B1", PixPayload.crc16("123456789")) }
    @Test fun buildsStaticFreeAmountPayload() {
        val payload = PixPayload.build(PixRequest("kevyn@example.com", "Loja Exemplo", "Campina Grande"))
        assertTrue(PixPayload.validate(payload))
        assertTrue(payload.contains("010211"))
        assertFalse(payload.contains("5401"))
    }
    @Test fun buildsAmountPayload() {
        val payload = PixPayload.build(PixRequest("kevyn@example.com", "Loja Exemplo", "Campina Grande", BigDecimal("12.50")))
        assertTrue(PixPayload.validate(payload))
        assertTrue(payload.contains("540512.50"))
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroAmount() { PixPayload.build(PixRequest("x@y.com", "Loja", "Joao Pessoa", BigDecimal.ZERO)) }
    @Test fun detectsCrcCorruption() {
        val payload = PixPayload.build(PixRequest("x@y.com", "Loja", "Joao Pessoa"))
        assertFalse(PixPayload.validate(payload.dropLast(1) + "0"))
    }
}
