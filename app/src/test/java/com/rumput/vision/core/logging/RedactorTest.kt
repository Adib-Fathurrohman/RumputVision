package com.rumput.vision.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RedactorTest {
    private fun assertRedacted(input: String, secret: String) {
        val out = Redactor.redact(input)
        assertFalse("'$secret' leaked in: $out", out.contains(secret))
    }

    @Test
    fun `redacts credentials embedded in URLs`() {
        assertEquals(
            "src rtsp://***@192.168.1.201:554/1/1",
            Redactor.redact("src rtsp://admin:hunter2@192.168.1.201:554/1/1"),
        )
        assertRedacted("""{"url":"https://user:p4ss@host/x"}""", "p4ss")
    }

    @Test
    fun `redacts authorization headers and bearer tokens`() {
        assertRedacted("Authorization: Bearer abc.def.ghi", "abc.def.ghi")
        assertRedacted("authorization=Basic dXNlcjpwYXNz", "dXNlcjpwYXNz")
        assertRedacted("sending bearer tok_123456", "tok_123456")
    }

    @Test
    fun `redacts cookies and the frigate token`() {
        assertRedacted("Set-Cookie: frigate_token=eyJhbGciOi.payload.sig; HttpOnly", "eyJhbGciOi")
        assertRedacted("Cookie: session=abc123", "abc123")
        assertRedacted("url?frigate_token=secretvalue", "secretvalue")
    }

    @Test
    fun `redacts password and token fields`() {
        assertRedacted("""{"user":"admin","password":"hunter2"}""", "hunter2")
        assertRedacted("password=hunter2&user=admin", "hunter2")
        assertRedacted("""{"access_token":"t0k3n"}""", "t0k3n")
    }

    @Test
    fun `redacts bare JWTs`() {
        assertRedacted("token eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.c2lnbmF0dXJl end", "eyJzdWIiOiJhZG1pbiJ9")
    }

    @Test
    fun `leaves ordinary text alone`() {
        val text = "GET https://frigate.home.arpa/api/version -> 200 (0.18.2-cfa08c5)"
        assertEquals(text, Redactor.redact(text))
    }
}
