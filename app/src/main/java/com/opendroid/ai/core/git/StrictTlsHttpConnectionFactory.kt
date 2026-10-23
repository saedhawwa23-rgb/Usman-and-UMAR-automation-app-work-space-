package com.opendroid.ai.core.git

import org.eclipse.jgit.transport.http.HttpConnection
import org.eclipse.jgit.transport.http.HttpConnectionFactory2
import org.eclipse.jgit.transport.http.JDKHttpConnectionFactory
import java.net.Proxy
import java.net.URL

/**
 * Keeps JGit's HTTP transport on the platform's normal certificate and hostname verification path.
 * JGit otherwise allows its sslVerify configuration to select a trust-all manager.
 */
internal class StrictTlsHttpConnectionFactory(
    private val delegate: HttpConnectionFactory2 = JDKHttpConnectionFactory()
) : HttpConnectionFactory2 {
    override fun create(url: URL): HttpConnection = delegate.create(url)

    override fun create(url: URL, proxy: Proxy): HttpConnection = delegate.create(url, proxy)

    override fun newSession(): HttpConnectionFactory2.GitSession {
        val session = delegate.newSession()
        return object : HttpConnectionFactory2.GitSession {
            override fun configure(connection: HttpConnection, sslVerify: Boolean): HttpConnection =
                session.configure(connection, true)

            override fun close() = session.close()
        }
    }
}
