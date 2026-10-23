package com.opendroid.ai.core.git

import org.eclipse.jgit.transport.http.HttpConnection
import org.eclipse.jgit.transport.http.HttpConnectionFactory2
import org.eclipse.jgit.transport.http.JDKHttpConnectionFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.Proxy
import java.net.URL

class StrictTlsHttpConnectionFactoryTest {
    @Test
    fun alwaysEnablesCertificateVerificationEvenWhenJgitRequestsTrustAll() {
        val requestedVerification = mutableListOf<Boolean>()
        val recordingDelegate = object : HttpConnectionFactory2 {
            private val platformFactory = JDKHttpConnectionFactory()

            override fun create(url: URL): HttpConnection = platformFactory.create(url)

            override fun create(url: URL, proxy: Proxy): HttpConnection = platformFactory.create(url, proxy)

            override fun newSession(): HttpConnectionFactory2.GitSession =
                object : HttpConnectionFactory2.GitSession {
                    override fun configure(connection: HttpConnection, sslVerify: Boolean): HttpConnection {
                        requestedVerification += sslVerify
                        return connection
                    }

                    override fun close() = Unit
                }
        }

        val strictFactory = StrictTlsHttpConnectionFactory(recordingDelegate)
        val connection = strictFactory.create(URL("https://github.com/"))
        strictFactory.newSession().configure(connection, false)

        assertEquals(listOf(true), requestedVerification)
    }
}
