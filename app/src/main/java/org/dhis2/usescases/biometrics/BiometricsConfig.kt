package org.dhis2.usescases.biometrics

data class BiometricsConfig(
    val orgUnitGroup: String,
    val projectId: String,
    val confidenceScoreFilter: Int?,
    val icon: String?,
    val lastVerificationDuration: Int?
)