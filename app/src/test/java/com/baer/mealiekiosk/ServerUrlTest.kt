package com.baer.mealiekiosk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlTest {
    @Test fun acceptsHttpAndHttpsUrls() {
        assertEquals("https://mealie.example.com/", ServerUrl.parse("https://mealie.example.com/"))
        assertEquals("http://192.168.1.20:9000", ServerUrl.parse("http://192.168.1.20:9000"))
        assertEquals("https://mealie.example.com/g/home?x=1", ServerUrl.parse("https://mealie.example.com/g/home?x=1"))
    }

    @Test fun addsHttpsAndTrimsWhenTypedWithoutScheme() {
        assertEquals("https://mealie.example.com", ServerUrl.parse("  mealie.example.com "))
        assertEquals("https://mealie.local:9000/", ServerUrl.parse("mealie.local:9000/"))
    }

    @Test fun rejectsEverythingElse() {
        assertNull(ServerUrl.parse(""))
        assertNull(ServerUrl.parse("   "))
        assertNull(ServerUrl.parse("https://"))
        assertNull(ServerUrl.parse("ftp://mealie.example.com"))
        assertNull(ServerUrl.parse("javascript:alert(1)"))
        assertNull(ServerUrl.parse("mealie example com"))
    }

    @Test fun onlyTheServerHostStaysInTheApp() {
        val base = "https://mealie.example.com/"
        assertTrue(ServerUrl.sameHost("https://mealie.example.com/g/home/r/soup", base))
        assertTrue(ServerUrl.sameHost("https://MEALIE.example.com/", base))
        assertFalse(ServerUrl.sameHost("https://evil.mealie.example.com/", base))
        assertFalse(ServerUrl.sameHost("https://github.com/mealie-recipes", base))
        assertFalse(ServerUrl.sameHost("not a url", base))
    }
}
