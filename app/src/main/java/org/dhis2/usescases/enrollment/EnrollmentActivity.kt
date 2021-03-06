package org.dhis2.usescases.enrollment

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.dhis2.App
import org.dhis2.R
import org.dhis2.commons.dialogs.AlertBottomDialog
import org.dhis2.data.biometrics.BiometricsClientFactory
import org.dhis2.data.biometrics.RegisterResult
import org.dhis2.data.forms.dataentry.FormView
import org.dhis2.data.forms.dataentry.fields.display.DisplayViewModel
import org.dhis2.data.location.LocationProvider
import org.dhis2.databinding.EnrollmentActivityBinding
import org.dhis2.form.data.FormRepository
import org.dhis2.form.data.GeometryController
import org.dhis2.form.data.GeometryParserImpl
import org.dhis2.form.model.DispatcherProvider
import org.dhis2.form.model.FieldUiModel
import org.dhis2.uicomponents.map.views.MapSelectorActivity
import org.dhis2.usescases.biometrics.BIOMETRICS_ENROLL_LAST_REQUEST
import org.dhis2.usescases.biometrics.BIOMETRICS_ENROLL_REQUEST
import org.dhis2.usescases.biometrics.duplicates.BiometricsDuplicatesDialog
import org.dhis2.usescases.eventsWithoutRegistration.eventCapture.EventCaptureActivity
import org.dhis2.usescases.eventsWithoutRegistration.eventInitial.EventInitialActivity
import org.dhis2.usescases.general.ActivityGlobalAbstract
import org.dhis2.usescases.teiDashboard.TeiDashboardMobileActivity
import org.dhis2.utils.Constants
import org.dhis2.utils.Constants.CAMERA_REQUEST
import org.dhis2.utils.Constants.ENROLLMENT_UID
import org.dhis2.utils.Constants.GALLERY_REQUEST
import org.dhis2.utils.Constants.PROGRAM_UID
import org.dhis2.utils.Constants.TEI_UID
import org.dhis2.utils.EventMode
import org.dhis2.utils.FileResourcesUtil
import org.dhis2.utils.ImageUtils
import org.dhis2.utils.RulesUtilsProviderConfigurationError
import org.dhis2.utils.customviews.ImageDetailBottomDialog
import org.dhis2.utils.toMessage
import org.hisp.dhis.android.core.arch.helpers.FileResourceDirectoryHelper
import org.hisp.dhis.android.core.common.FeatureType
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus
import java.io.File
import javax.inject.Inject

class EnrollmentActivity : ActivityGlobalAbstract(), EnrollmentView {

    enum class EnrollmentMode { NEW, CHECK }

    private var forRelationship: Boolean = false
    private lateinit var formView: FormView

    @Inject
    lateinit var presenter: EnrollmentPresenterImpl

    @Inject
    lateinit var formRepository: FormRepository

    @Inject
    lateinit var locationProvider: LocationProvider

    @Inject
    lateinit var dispatchers: DispatcherProvider

    lateinit var binding: EnrollmentActivityBinding
    lateinit var mode: EnrollmentMode

    companion object {
        const val ENROLLMENT_UID_EXTRA = "ENROLLMENT_UID_EXTRA"
        const val PROGRAM_UID_EXTRA = "PROGRAM_UID_EXTRA"
        const val MODE_EXTRA = "MODE_EXTRA"
        const val FOR_RELATIONSHIP = "FOR_RELATIONSHIP"
        const val RQ_ENROLLMENT_GEOMETRY = 1023
        const val RQ_INCIDENT_GEOMETRY = 1024
        const val RQ_EVENT = 1025
        const val RQ_GO_BACK = 1026

        fun getIntent(
            context: Context,
            enrollmentUid: String,
            programUid: String,
            enrollmentMode: EnrollmentMode,
            forRelationship: Boolean? = false
        ): Intent {
            val intent = Intent(context, EnrollmentActivity::class.java)
            intent.putExtra(ENROLLMENT_UID_EXTRA, enrollmentUid)
            intent.putExtra(PROGRAM_UID_EXTRA, programUid)
            intent.putExtra(MODE_EXTRA, enrollmentMode.name)
            intent.putExtra(FOR_RELATIONSHIP, forRelationship)
            if (forRelationship == true) {
                intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
            }
            return intent
        }
    }

    /*region LIFECYCLE*/

    override fun onCreate(savedInstanceState: Bundle?) {
        val enrollmentUid = intent.getStringExtra(ENROLLMENT_UID_EXTRA) ?: ""
        val programUid = intent.getStringExtra(PROGRAM_UID_EXTRA) ?: ""
        val enrollmentMode = intent.getStringExtra(MODE_EXTRA)?.let { EnrollmentMode.valueOf(it) }
            ?: EnrollmentMode.NEW
        (applicationContext as App).userComponent()!!.plus(
            EnrollmentModule(
                this,
                enrollmentUid,
                programUid,
                enrollmentMode,
                context
            )
        ).inject(this)

        formView = FormView.Builder()
            .repository(formRepository)
            .locationProvider(locationProvider)
            .dispatcher(dispatchers)
            .onItemChangeListener { action -> presenter.updateFields(action) }
            .onLoadingListener { loading ->
                if (loading) {
                    showProgress()
                } else {
                    hideProgress()
                }
            }
            .factory(supportFragmentManager)
            .build()

        super.onCreate(savedInstanceState)

        if (presenter.getEnrollment() == null ||
            presenter.getEnrollment()?.trackedEntityInstance() == null
        ) {
            finish()
        }

        forRelationship = intent.getBooleanExtra(FOR_RELATIONSHIP, false)
        binding = DataBindingUtil.setContentView(this, R.layout.enrollment_activity)
        binding.view = this

        mode = enrollmentMode

        val fragmentTransaction = supportFragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.formViewContainer, formView)
        fragmentTransaction.commit()

        binding.save.setOnClickListener {
            performSaveClick()
        }

        presenter.init()
    }

    override fun onResume() {
        presenter.subscribeToBackButton()
        super.onResume()
    }

    override fun onDestroy() {
        presenter.onDettach()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                RQ_INCIDENT_GEOMETRY, RQ_ENROLLMENT_GEOMETRY -> {
                    if (data?.hasExtra(MapSelectorActivity.DATA_EXTRA) == true) {
                        handleGeometry(
                            FeatureType.valueOfFeatureType(
                                data.getStringExtra(MapSelectorActivity.LOCATION_TYPE_EXTRA)
                            ),
                            data.getStringExtra(MapSelectorActivity.DATA_EXTRA)!!, requestCode
                        )
                    }
                }
                GALLERY_REQUEST -> {
                    try {
                        val imageUri = data?.data
                        presenter.saveFile(
                            uuid,
                            FileResourcesUtil.getFileFromGallery(this, imageUri).path
                        )
                        presenter.updateFields()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(
                            this, getString(R.string.something_wrong), Toast.LENGTH_LONG
                        ).show()
                    }
                }
                CAMERA_REQUEST -> {
                    val imageFile = File(
                        FileResourceDirectoryHelper.getFileResourceDirectory(this),
                        "tempFile.png"
                    )

                    val file = ImageUtils().rotateImage(this, imageFile)

                    try {
                        presenter.saveFile(uuid, if (file.exists()) file.path else null)
                        presenter.updateFields()
                    } catch (e: Exception) {
                        crashReportController.logException(e)
                        Toast.makeText(
                            this, getString(R.string.something_wrong), Toast.LENGTH_LONG
                        ).show()
                    }
                }
                BIOMETRICS_ENROLL_REQUEST -> {
                    if (data != null) {
                        when (val result = BiometricsClientFactory.get(this).handleRegisterResponse(
                            data
                        )) {
                            is RegisterResult.Completed -> {
                                presenter.onBiometricsCompleted(result.guid)
                            }
                            is RegisterResult.Failure -> {
                                presenter.onBiometricsFailure()
                            }
                            is RegisterResult.PossibleDuplicates -> {
                                presenter.onBiometricsPossibleDuplicates(
                                    result.guids,
                                    result.sessionId
                                )
                            }
                        }
                    }
                }
                BIOMETRICS_ENROLL_LAST_REQUEST -> {
                    if (resultCode == RESULT_OK) {
                        if (data != null) {
                            when (val result =
                                BiometricsClientFactory.get(this).handleRegisterResponse(data)) {
                                is RegisterResult.Completed -> {
                                    presenter.onBiometricsCompleted(result.guid)
                                }
                                else -> {
                                    presenter.onBiometricsFailure()
                                }
                            }
                        }
                    }
                }
                RQ_EVENT -> openDashboard(presenter.getEnrollment()!!.uid()!!)
            }
        }
    }

    override fun openEvent(eventUid: String) {
        if (presenter.openInitial(eventUid)) {
            val bundle = EventInitialActivity.getBundle(
                presenter.getProgram().uid(),
                eventUid,
                null,
                presenter.getEnrollment()!!.trackedEntityInstance(),
                null,
                presenter.getEnrollment()!!.organisationUnit(),
                presenter.getEventStage(eventUid),
                presenter.getEnrollment()!!.uid(),
                0,
                presenter.getEnrollment()!!.status()
            )
            val eventInitialIntent = Intent(abstracContext, EventInitialActivity::class.java)
            eventInitialIntent.putExtras(bundle)
            startActivityForResult(eventInitialIntent, RQ_EVENT)
        } else {
            val eventCreationIntent = Intent(abstracContext, EventCaptureActivity::class.java)
            eventCreationIntent.putExtras(
                EventCaptureActivity.getActivityBundle(
                    eventUid,
                    presenter.getProgram().uid(),
                    EventMode.CHECK
                )
            )
            eventCreationIntent.putExtra(
                Constants.TRACKED_ENTITY_INSTANCE,
                presenter.getEnrollment()!!.trackedEntityInstance()
            )
            startActivityForResult(eventCreationIntent, RQ_EVENT)
        }
    }

    override fun openDashboard(enrollmentUid: String) {
        if (forRelationship) {
            val intent = Intent()
            intent.putExtra("TEI_A_UID", presenter.getEnrollment()!!.trackedEntityInstance())
            setResult(Activity.RESULT_OK, intent)
            finish()
        } else {
            val bundle = Bundle()
            bundle.putString(PROGRAM_UID, presenter.getProgram().uid())
            bundle.putString(TEI_UID, presenter.getEnrollment()!!.trackedEntityInstance())
            bundle.putString(ENROLLMENT_UID, enrollmentUid)
            startActivity(TeiDashboardMobileActivity::class.java, bundle, true, false, null)
        }
    }

    override fun showMissingMandatoryFieldsMessage(
        emptyMandatoryFields: MutableMap<String, String>
    ) {
        AlertBottomDialog.instance
            .setTitle(getString(R.string.unable_to_save))
            .setMessage(getString(R.string.missing_mandatory_fields))
            .setFieldsToDisplay(emptyMandatoryFields.keys.toList())
            .show(supportFragmentManager, AlertBottomDialog::class.java.simpleName)
    }

    override fun showErrorFieldsMessage(errorFields: List<String>) {
        AlertBottomDialog.instance
            .setTitle(getString(R.string.unable_to_save))
            .setMessage(getString(R.string.field_errors))
            .setFieldsToDisplay(errorFields)
            .show(supportFragmentManager, AlertBottomDialog::class.java.simpleName)
    }

    override fun showWarningFieldsMessage(warningFields: List<String>) {
        AlertBottomDialog.instance
            .setTitle(getString(R.string.warnings_in_form))
            .setMessage(getString(R.string.what_to_do))
            .setFieldsToDisplay(warningFields)
            .setNegativeButton(getString(R.string.review))
            .setPositiveButton(getString(R.string.save)) { presenter.finish(mode) }
            .show(supportFragmentManager, AlertBottomDialog::class.java.simpleName)
    }

    override fun goBack() {
        onBackPressed()
    }

    override fun onBackPressed() {
        formView.onEditionFinish()
        attemptFinish()
    }

    private fun attemptFinish() {
        if (mode == EnrollmentMode.CHECK) {
            presenter.backIsClicked()
        } else {
            showDeleteDialog()
        }
    }

    private fun showDeleteDialog() {
        AlertBottomDialog.instance
            .setTitle(getString(R.string.title_delete_go_back))
            .setMessage(getString(R.string.delete_go_back))
            .setPositiveButton(getString(R.string.missing_mandatory_fields_go_back)) {
                presenter.deleteAllSavedData()
                finish()
            }
            .setNegativeButton()
            .show(supportFragmentManager, AlertBottomDialog::class.java.simpleName)
    }

    private fun handleGeometry(featureType: FeatureType, dataExtra: String, requestCode: Int) {
        val geometry = GeometryController(GeometryParserImpl()).generateLocationFromCoordinates(
            featureType,
            dataExtra
        )

        if (geometry != null) {
            when (requestCode) {
                RQ_ENROLLMENT_GEOMETRY -> {
                    presenter.saveEnrollmentGeometry(geometry)
                }
                RQ_INCIDENT_GEOMETRY -> {
                    presenter.saveTeiGeometry(geometry)
                }
            }
        }
    }

    override fun setResultAndFinish() {
        setResult(RESULT_OK)
        finish()
    }

    /*endregion*/

    /*region TEI*/
    override fun displayTeiInfo(attrList: List<String>, profileImage: String) {
        if (mode != EnrollmentMode.NEW) {
            binding.title.visibility = View.GONE
            binding.teiDataHeader.root.visibility = View.VISIBLE

            val attrListNotEmpty = attrList.filter { it.isNotEmpty() }
            binding.teiDataHeader.mainAttributes.apply {
                when (attrListNotEmpty.size) {
                    0 -> visibility = View.GONE
                    1 -> text = attrListNotEmpty[0]
                    else -> text = String.format("%s %s", attrListNotEmpty[0], attrListNotEmpty[1])
                }
                setTextColor(Color.WHITE)
            }
            binding.teiDataHeader.secundaryAttribute.apply {
                when (attrListNotEmpty.size) {
                    0, 1, 2 -> visibility = View.GONE
                    else -> text = attrListNotEmpty[2]
                }
                setTextColor(Color.WHITE)
            }

            if (profileImage.isEmpty()) {
                binding.teiDataHeader.teiImage.visibility = View.GONE
                binding.teiDataHeader.imageSeparator.visibility = View.GONE
            } else {
                Glide.with(this).load(File(profileImage))
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .transform(CircleCrop())
                    .into(binding.teiDataHeader.teiImage)
                binding.teiDataHeader.teiImage.setOnClickListener {
                    presenter.onTeiImageHeaderClick()
                }
            }
        } else {
            binding.title.visibility = View.VISIBLE
            binding.teiDataHeader.root.visibility = View.GONE
            binding.title.text =
                String.format(getString(R.string.enroll_in), presenter.getProgram().displayName())
        }
    }

    override fun displayTeiPicture(picturePath: String) {
        ImageDetailBottomDialog(
            null,
            File(picturePath)
        ).show(
            supportFragmentManager,
            ImageDetailBottomDialog.TAG
        )
    }
    /*endregion*/
    /*region ACCESS*/

    override fun setAccess(access: Boolean?) {
        if (access == false) {
            binding.save.visibility = View.GONE
        }
    }
    /*endregion*/

    /*region STATUS*/

    override fun renderStatus(status: EnrollmentStatus) {
        binding.enrollmentStatus = status
    }

    override fun showStatusOptions(currentStatus: EnrollmentStatus) {
    }

    /*endregion*/

    /*region DATA ENTRY*/
    override fun showFields(fields: List<FieldUiModel>?) {
        fields?.filter {
            it !is DisplayViewModel
        }

        formView.processItems(fields)
    }

    /*endregion*/
    override fun requestFocus() {
        binding.root.requestFocus()
    }

    override fun setSaveButtonVisible(visible: Boolean) {
        if (visible) {
            binding.save.show()
        } else {
            binding.save.hide()
        }
    }

    override fun performSaveClick() {
        if (currentFocus is EditText) {
            presenter.setFinishing()
            currentFocus?.apply { clearFocus() }
        } else {
            if (!presenter.hasAccess() || presenter.dataIntegrityCheck()) {
                presenter.finish(mode)
            }
        }
    }

    override fun showProgress() {
        runOnUiThread {
            binding.toolbarProgress.show()
        }
    }

    override fun hideProgress() {
        runOnUiThread {
            binding.toolbarProgress.hide()
        }
    }

    override fun showDateEditionWarning() {
        val dialog = MaterialAlertDialogBuilder(this, R.style.DhisMaterialDialog)
            .setMessage(R.string.enrollment_date_edition_warning)
            .setPositiveButton(R.string.button_ok, null)
        dialog.show()
    }

    override fun displayConfigurationErrors(
        configurationError: List<RulesUtilsProviderConfigurationError>
    ) {
        MaterialAlertDialogBuilder(this, R.style.DhisMaterialDialog)
            .setTitle(R.string.warning_error_on_complete_title)
            .setMessage(configurationError.toMessage(this))
            .setPositiveButton(
                R.string.action_close
            ) { _, _ -> }
            .setNegativeButton(
                getString(R.string.action_do_not_show_again)
            ) { _, _ -> presenter.disableConfErrorMessage() }
            .setCancelable(false)
            .show()
    }

    override fun registerBiometrics(orgUnit: String) {
        BiometricsClientFactory.get(this).register(this, orgUnit)
    }

    override fun showPossibleDuplicatesDialog(
        guids: List<String>, sessionId: String, programUid: String,
        trackedEntityTypeUid: String,
        biometricsAttributeUid: String
    ) {
        val dialog = BiometricsDuplicatesDialog.newInstance(
            guids, sessionId, programUid,
            trackedEntityTypeUid,
            biometricsAttributeUid
        )

        dialog.setOnOpenTeiDashboardListener { teiUid: String, programUid: String, enrollmentUid: String ->
            presenter.deleteAllSavedData()
            finish()
            startActivity(
                TeiDashboardMobileActivity.intent(
                    this,
                    teiUid,
                    programUid,
                    enrollmentUid
                )
            )
        }

        dialog.setOnEnrollNewListener { biometricsSessionId ->
            BiometricsClientFactory.get(this).registerLast(this, biometricsSessionId)
        }

        dialog.show(
            supportFragmentManager,
            BiometricsDuplicatesDialog.TAG
        )
    }

    override fun registerLast(sessionId: String) {
        BiometricsClientFactory.get(this).registerLast(this, sessionId)
    }
}
