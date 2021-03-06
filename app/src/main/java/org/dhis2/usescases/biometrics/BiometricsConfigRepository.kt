package org.dhis2.usescases.biometrics

interface BiometricsConfigRepository {
    fun sync()
}

enum class BiometricsIcon { FACE, FINGERPRINT }