package us.bergnet.oversight.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * A home screen app (Foyer) that registered through POST /host. [remapActive] and [packageName]
 * persist; [homeVisibleUntil] is a lease deadline (epoch ms) that is renewed while the host's home
 * screen is showing and runs out on its own if the host stops renewing (e.g. it crashed).
 */
@Serializable
data class HostState(
    val packageName: String? = null,
    val remapActive: Boolean = false,
    @Transient val homeVisibleUntil: Long = 0L,
) {
    fun isHomeVisible(now: Long): Boolean = packageName != null && now < homeVisibleUntil

    /** What GET /info and POST /host report; null when no host is registered. */
    fun report(now: Long): HostReport? =
        packageName?.let { HostReport(it, homeVisible = isHomeVisible(now), remapActive = remapActive) }

    /** This state with a request applied; omitted fields keep their value. */
    fun apply(request: HostRequest, now: Long): HostState {
        val lease = (request.leaseSeconds ?: DEFAULT_LEASE_SECONDS).coerceIn(LEASE_SECONDS)
        return copy(
            packageName = request.packageName ?: packageName,
            remapActive = request.remapActive ?: remapActive,
            homeVisibleUntil = when (request.homeVisible) {
                true -> now + lease * 1000L
                false -> 0L
                null -> homeVisibleUntil
            },
        )
    }

    companion object {
        const val FOYER_PACKAGE = "us.bergnet.foyer"
        /** Only these apps may register; the package in a request is self-reported, so this limits who can claim it. */
        val ALLOWED_PACKAGES = setOf(FOYER_PACKAGE)
        val LEASE_SECONDS = 5..300
        const val DEFAULT_LEASE_SECONDS = 30
    }
}

@Serializable
data class HostReport(
    @SerialName("package") val packageName: String,
    val homeVisible: Boolean,
    val remapActive: Boolean,
)

@Serializable
data class HostRequest(
    @SerialName("package") val packageName: String? = null,
    val homeVisible: Boolean? = null,
    val remapActive: Boolean? = null,
    val leaseSeconds: Int? = null,
)
