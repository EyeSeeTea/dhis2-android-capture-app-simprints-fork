package org.dhis2.data.biometrics

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.simprints.libsimprints.Constants
import com.simprints.libsimprints.Identification
import com.simprints.libsimprints.RefusalForm
import com.simprints.libsimprints.Registration
import com.simprints.libsimprints.SimHelper
import com.simprints.libsimprints.Tier
import com.simprints.libsimprints.Verification
import org.dhis2.R
import org.dhis2.usescases.biometrics.BIOMETRICS_CONFIRM_IDENTITY_REQUEST
import org.dhis2.usescases.biometrics.BIOMETRICS_ENROLL_LAST_REQUEST
import org.dhis2.usescases.biometrics.BIOMETRICS_ENROLL_REQUEST
import org.dhis2.usescases.biometrics.BIOMETRICS_IDENTIFY_REQUEST
import org.dhis2.usescases.biometrics.BIOMETRICS_VERIFY_REQUEST
import timber.log.Timber
import java.util.ArrayList

sealed class RegisterResult {
    data class Completed(val guid: String) : RegisterResult()
    data class PossibleDuplicates(val guids: List<String>, val sessionId: String) : RegisterResult()
    object Failure : RegisterResult()
}

sealed class IdentifyResult {
    data class Completed(val guids: List<String>, val sessionId: String) : IdentifyResult()
    object BiometricsDeclined : IdentifyResult()
    data class UserNotFound(val sessionId: String) : IdentifyResult()
    object Failure : IdentifyResult()
}

sealed class VerifyResult {
    object Match : VerifyResult()
    object NoMatch : VerifyResult()
    object Failure : VerifyResult()
}

class BiometricsClient(
    projectId: String,
    userId: String,
    private val confidenceScoreFilter: Int
) {

    init {
        Timber.d("BiometricsClient!")
        Timber.d("userId: $userId")
        Timber.d("projectId: $projectId")
        Timber.d("confidenceScoreFilter: $confidenceScoreFilter")
    }

    private val simHelper = SimHelper(projectId, userId)
    private val defaultModuleId = "NA"

    fun register(activity: Activity, moduleId: String) {
        Timber.d("Biometrics register!")
        Timber.d("moduleId: $moduleId")

        val intent = simHelper.register(moduleId)

        if (checkSimprintsApp(activity, intent)) {
            activity.startActivityForResult(intent, BIOMETRICS_ENROLL_REQUEST)
        }
    }

    fun identify(activity: Activity) {
        Timber.d("Biometrics identify!")
        Timber.d("moduleId: $defaultModuleId")

        val intent = simHelper.identify(defaultModuleId)

        if (checkSimprintsApp(activity, intent)) {
            activity.startActivityForResult(intent, BIOMETRICS_IDENTIFY_REQUEST)
        }
    }

    fun verify(fragment: Fragment, guid: String, moduleId: String) {
        if (guid == null) {
            Timber.i("Simprints Verification - Guid is Null - Please check again!")
            return
        }

        Timber.d("Biometrics verify!")
        Timber.d("moduleId: $moduleId")

        val intent = simHelper.verify(moduleId, guid)

        if (fragment.context != null && checkSimprintsApp(fragment.requireContext(), intent)) {
            fragment.startActivityForResult(intent, BIOMETRICS_VERIFY_REQUEST)
        }
    }

    fun handleRegisterResponse(data: Intent): RegisterResult {
        val biometricsCompleted = checkBiometricsCompleted(data)

        val handleRegister = {
            val registration: Registration? =
                data.getParcelableExtra(Constants.SIMPRINTS_REGISTRATION)

            if (registration == null) {
                RegisterResult.Failure
            } else {
                RegisterResult.Completed(registration?.guid)
            }
        }

        val handlePossibleDuplicates = {
            when (val identifyResponse = handleIdentifyResponse(data)) {
                is IdentifyResult.Completed -> {
                    Timber.d("Possible duplicates ${identifyResponse.guids}")
                    RegisterResult.PossibleDuplicates(
                        identifyResponse.guids,
                        identifyResponse.sessionId
                    )
                }
                is IdentifyResult.BiometricsDeclined -> {
                    RegisterResult.Failure
                }
                is IdentifyResult.UserNotFound -> {
                    handleRegister()
                }
                is IdentifyResult.Failure -> {
                    RegisterResult.Failure
                }
            }
        }

        return if (biometricsCompleted) {
            when {
                data.hasExtra(Constants.SIMPRINTS_IDENTIFICATIONS) -> {
                    handlePossibleDuplicates()
                }
                data.hasExtra(Constants.SIMPRINTS_REGISTRATION) -> {
                    handleRegister()
                }
                else -> {
                    RegisterResult.Failure
                }
            }
        } else {
            RegisterResult.Failure
        }
    }

    fun handleIdentifyResponse(data: Intent): IdentifyResult {
        val biometricsCompleted = checkBiometricsCompleted(data)

        if (biometricsCompleted) {
            val identifications: ArrayList<Identification>? = data.getParcelableArrayListExtra(
                Constants.SIMPRINTS_IDENTIFICATIONS
            )
            val refusalForm: RefusalForm? =
                data.getParcelableExtra(Constants.SIMPRINTS_REFUSAL_FORM)

            val sessionId: String = data.getStringExtra(Constants.SIMPRINTS_SESSION_ID) ?: ""

            return if (identifications == null && refusalForm != null) {
                IdentifyResult.BiometricsDeclined
            } else if (identifications == null || identifications.size == 0) {
                IdentifyResult.UserNotFound(sessionId)
            } else {
                val finalIdentifications =
                    identifications.filter { it.confidence >= confidenceScoreFilter }

                if (finalIdentifications.isEmpty()) {
                    Timber.w("Identify returns data but no match with confidence score filter")
                    IdentifyResult.UserNotFound(sessionId)
                } else {
                    IdentifyResult.Completed(finalIdentifications.map { it.guid }, sessionId)
                }
            }
        } else {
            return IdentifyResult.Failure
        }
    }

    fun handleVerifyResponse(data: Intent): VerifyResult {
        val biometricsCompleted = checkBiometricsCompleted(data)

        return if (biometricsCompleted) {
            val verification: Verification? =
                data.getParcelableExtra(Constants.SIMPRINTS_VERIFICATION)

            if (verification != null) {
                when (verification.tier) {
                    Tier.TIER_1, Tier.TIER_2, Tier.TIER_3, Tier.TIER_4 -> {
                        if (verification.confidence >= confidenceScoreFilter) {
                            VerifyResult.Match
                        } else {
                            Timber.w("Verify returns data but no match with confidence score filter")
                            VerifyResult.NoMatch
                        }
                    }
                    Tier.TIER_5 -> VerifyResult.NoMatch
                }
            } else {
                VerifyResult.Failure
            }
        } else {
            VerifyResult.Failure
        }
    }

    fun confirmIdentify(activity: Activity, sessionId: String, guid: String) {
        Timber.d("Biometrics confirmIdentify!")
        Timber.d("sessionId: $sessionId")
        Timber.d("guid: $guid")

        val intent = simHelper.confirmIdentity(activity, sessionId, guid)

        if (checkSimprintsApp(activity, intent)) {
            activity.startActivityForResult(intent, BIOMETRICS_CONFIRM_IDENTITY_REQUEST)
        }
    }

    fun confirmIdentify(fragment: Fragment, sessionId: String, guid: String) {
        Timber.d("Biometrics confirmIdentify!")
        Timber.d("sessionId: $sessionId")
        Timber.d("guid: $guid")

        val intent = simHelper.confirmIdentity(fragment.requireContext(), sessionId, guid)

        if (checkSimprintsApp(fragment.requireContext(), intent)) {
            fragment.startActivityForResult(intent, BIOMETRICS_CONFIRM_IDENTITY_REQUEST)
        }
    }

    fun noneSelected(activity: Activity, sessionId: String) {
        Timber.d("Biometrics confirmIdentify!")
        Timber.d("sessionId: $sessionId")
        Timber.d("guid: none_selected")

        val intent = simHelper.confirmIdentity(activity, sessionId, "none_selected")

        if (checkSimprintsApp(activity, intent)) {
            activity.startActivityForResult(intent, BIOMETRICS_CONFIRM_IDENTITY_REQUEST)
        }
    }

    fun registerLast(activity: Activity, sessionId: String) {
        Timber.d("Biometrics confirmIdentify!")
        Timber.d("moduleId: $defaultModuleId")
        Timber.d("sessionId: $sessionId")

        val intent = simHelper.registerLastBiometrics(defaultModuleId, sessionId)

        if (checkSimprintsApp(activity, intent)) {
            activity.startActivityForResult(intent, BIOMETRICS_ENROLL_LAST_REQUEST)
        }
    }

    private fun checkBiometricsCompleted(data: Intent) =
        data.getBooleanExtra(Constants.SIMPRINTS_BIOMETRICS_COMPLETE_CHECK, false)

    private fun checkSimprintsApp(context: Context, intent: Intent): Boolean {
        val manager: PackageManager = context.packageManager
        val info = manager.queryIntentActivities(intent, 0)
        return if (info.size > 0) {
            true
        } else {
            Toast.makeText(context, R.string.biometrics_download_app, Toast.LENGTH_SHORT).show()
            false
        }
    }
}