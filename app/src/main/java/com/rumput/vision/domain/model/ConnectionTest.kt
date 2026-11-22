package com.rumput.vision.domain.model

data class ConnectionTestReport(val results: List<EndpointTestResult>)

data class EndpointTestResult(
    val route: ConnectionRoute,
    val url: String,
    val outcome: EndpointTestOutcome,
)

sealed interface EndpointTestOutcome {
    data class Reachable(val version: String?, val access: AccessCheck) : EndpointTestOutcome

    /** The server redirected; the user probably needs the redirect target (often an https:// URL). */
    data class Redirected(val location: String?) : EndpointTestOutcome

    data object NotFrigate : EndpointTestOutcome
    data class Failed(val error: FrigateError) : EndpointTestOutcome
}

sealed interface AccessCheck {
    /** The API answered without any credentials. `admin` here means authentication is effectively off. */
    data class Anonymous(val role: String?) : AccessCheck {
        val isAdmin: Boolean get() = role == "admin"
    }

    data class SignedIn(val username: String?, val role: String?) : AccessCheck

    /** Sign-in is required and no password was provided for the test. */
    data object SignInRequired : AccessCheck
    data object InvalidCredentials : AccessCheck
    data object RateLimited : AccessCheck

    /** The profile is set to "no authentication" but the server requires a sign-in. */
    data object ServerRequiresLogin : AccessCheck
    data class Failed(val error: FrigateError) : AccessCheck
}
