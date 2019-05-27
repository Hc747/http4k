package org.http4k.security

import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.TEMPORARY_REDIRECT
import org.http4k.core.toParameters
import org.http4k.security.openid.IdTokenConsumer
import org.http4k.security.openid.IdTokenContainer

class OAuthCallback(
    private val oAuthPersistence: OAuthPersistence,
    private val idTokenConsumer: IdTokenConsumer,
    private val accessTokenFetcher: AccessTokenFetcher
) : HttpHandler {

    override suspend fun invoke(request: Request): Response {
        val state = request.query("state")?.toParameters() ?: emptyList()
        val crsfInState = state.find { it.first == "csrf" }?.second?.let(::CrossSiteRequestForgeryToken)
        return request.query("code")?.let { code ->
            if (crsfInState != null && crsfInState == oAuthPersistence.retrieveCsrf(request)) {
                request.query("id_token")?.let { idTokenConsumer.consumeFromAuthorizationResponse(IdTokenContainer(it)) }
                accessTokenFetcher.fetch(code)?.let { tokenDetails ->
                    tokenDetails.idToken?.also { idTokenConsumer.consumeFromAccessTokenResponse(it) }

                    val originalUri = state.find { it.first == "uri" }?.second ?: "/"
                    oAuthPersistence.assignToken(request, Response(TEMPORARY_REDIRECT).header("Location", originalUri), tokenDetails.accessToken)
                }
            } else null
        } ?: oAuthPersistence.authFailureResponse()
    }
}