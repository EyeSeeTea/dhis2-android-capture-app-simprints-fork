package org.dhis2.usescases.teiDashboard.dashboardfragments.teidata;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ObservableBoolean;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.DividerItemDecoration;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;

import org.dhis2.App;
import org.dhis2.R;
import org.dhis2.commons.dialogs.CustomDialog;
import org.dhis2.commons.dialogs.DialogClickListener;
import org.dhis2.data.biometrics.BiometricsClientFactory;
import org.dhis2.data.biometrics.VerifyResult;
import org.dhis2.databinding.FragmentTeiDataBinding;
import org.dhis2.usescases.eventsWithoutRegistration.eventInitial.EventInitialActivity;
import org.dhis2.usescases.general.FragmentGlobalAbstract;
import org.dhis2.commons.orgunitselector.OUTreeFragment;
import org.dhis2.commons.orgunitselector.OnOrgUnitSelectionFinished;
import org.dhis2.usescases.programStageSelection.ProgramStageSelectionActivity;
import org.dhis2.usescases.teiDashboard.DashboardProgramModel;
import org.dhis2.usescases.teiDashboard.DashboardViewModel;
import org.dhis2.usescases.teiDashboard.TeiDashboardMobileActivity;
import org.dhis2.usescases.teiDashboard.dashboardfragments.teidata.teievents.EventAdapter;
import org.dhis2.usescases.teiDashboard.dashboardfragments.teidata.teievents.EventViewModel;
import org.dhis2.usescases.teiDashboard.dashboardfragments.teidata.teievents.EventViewModelType;
import org.dhis2.utils.Constants;
import org.dhis2.utils.DateUtils;
import org.dhis2.utils.EventCreationType;
import org.dhis2.utils.ObjectStyleUtils;
import org.dhis2.utils.category.CategoryDialog;
import org.dhis2.utils.customviews.ImageDetailBottomDialog;
import org.dhis2.utils.dialFloatingActionButton.DialItem;
import org.dhis2.commons.filters.FilterItem;
import org.dhis2.commons.filters.FilterManager;
import org.dhis2.commons.filters.FiltersAdapter;
import org.dhis2.utils.granularsync.SyncStatusDialog;
import org.hisp.dhis.android.core.enrollment.Enrollment;
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus;
import org.hisp.dhis.android.core.event.Event;
import org.hisp.dhis.android.core.organisationunit.OrganisationUnit;
import org.hisp.dhis.android.core.program.Program;
import org.hisp.dhis.android.core.program.ProgramStage;
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstance;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.functions.Consumer;
import kotlin.Unit;
import timber.log.Timber;

import static android.app.Activity.RESULT_OK;

import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;

import static org.dhis2.usescases.biometrics.BiometricConstantsKt.BIOMETRICS_GUID;
import static org.dhis2.usescases.biometrics.BiometricConstantsKt.BIOMETRICS_TEI_ORGANISATION_UNIT;
import static org.dhis2.usescases.biometrics.BiometricConstantsKt.BIOMETRICS_VERIFICATION_STATUS;
import static org.dhis2.usescases.biometrics.BiometricConstantsKt.BIOMETRICS_VERIFY_REQUEST;
import static org.dhis2.usescases.biometrics.ExtensionsKt.getBioIconBasic;
import static org.dhis2.usescases.biometrics.ExtensionsKt.getBioIconFailed;
import static org.dhis2.usescases.biometrics.ExtensionsKt.getBioIconSuccess;
import static org.dhis2.usescases.biometrics.ExtensionsKt.getBioIconWarning;
import static org.dhis2.utils.Constants.ENROLLMENT_UID;
import static org.dhis2.utils.Constants.EVENT_CREATION_TYPE;
import static org.dhis2.utils.Constants.EVENT_PERIOD_TYPE;
import static org.dhis2.utils.Constants.EVENT_REPEATABLE;
import static org.dhis2.utils.Constants.EVENT_SCHEDULE_INTERVAL;
import static org.dhis2.utils.Constants.ORG_UNIT;
import static org.dhis2.utils.Constants.PROGRAM_UID;
import static org.dhis2.utils.Constants.TRACKED_ENTITY_INSTANCE;
import static org.dhis2.utils.analytics.AnalyticsConstants.CREATE_EVENT_TEI;
import static org.dhis2.utils.analytics.AnalyticsConstants.TYPE_EVENT_TEI;

public class TEIDataFragment extends FragmentGlobalAbstract implements TEIDataContracts.View,
        OnOrgUnitSelectionFinished {

    private static final int REQ_DETAILS = 1001;
    private static final int REQ_EVENT = 2001;

    private static final int RC_GENERATE_EVENT = 1501;
    private static final int RC_EVENTS_COMPLETED = 1601;

    private static final int REFERAL_ID = 3;
    private static final int ADD_NEW_ID = 2;
    private static final int SCHEDULE_ID = 1;

    private static final String PREF_COMPLETED_EVENT = "COMPLETED_EVENT";

    private FragmentTeiDataBinding binding;

    @Inject
    TEIDataContracts.Presenter presenter;

    @Inject
    FilterManager filterManager;

    @Inject
    FiltersAdapter filtersAdapter;


    private EventAdapter adapter;
    private CustomDialog dialog;
    private ProgramStage programStageFromEvent;
    private final ObservableBoolean followUp = new ObservableBoolean(false);

    private boolean hasCatComb;
    private final ArrayList<Event> catComboShowed = new ArrayList<>();
    private Context context;
    private DashboardViewModel dashboardViewModel;
    private DashboardProgramModel dashboardModel;
    private TeiDashboardMobileActivity activity;
    private PopupMenu popupMenu;

    public static TEIDataFragment newInstance(String programUid, String teiUid,
            String enrollmentUid) {
        TEIDataFragment fragment = new TEIDataFragment();
        Bundle args = new Bundle();
        args.putString("PROGRAM_UID", programUid);
        args.putString("TEI_UID", teiUid);
        args.putString("ENROLLMENT_UID", enrollmentUid);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NotNull Context context) {
        super.onAttach(context);
        this.context = context;
        activity = (TeiDashboardMobileActivity) context;
        ((App) context.getApplicationContext())
                .dashboardComponent()
                .plus(new TEIDataModule(this,
                        getArguments().getString("PROGRAM_UID"),
                        getArguments().getString("TEI_UID"),
                        getArguments().getString("ENROLLMENT_UID")
                ))
                .inject(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        dashboardViewModel = ViewModelProviders.of(activity).get(DashboardViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_tei_data, container, false);
        binding.setPresenter(presenter);
        activity.observeGrouping().observe(getViewLifecycleOwner(), group -> {
            showLoadingProgress(true);
            binding.setIsGrouping(group);
            presenter.onGroupingChanged(group);
        });
        activity.observeFilters().observe(getViewLifecycleOwner(), this::showHideFilters);
        activity.updatedEnrollment().observe(getViewLifecycleOwner(), this::updateEnrollment);

        try {
            binding.filterLayout.setAdapter(filtersAdapter);
        } catch (Exception e) {
            Timber.e(e);
        }

        binding.cardFront.verificationButtonIcon.setImageDrawable(
                AppCompatResources.getDrawable(context, getBioIconBasic(context)));

        return binding.getRoot();
    }

    private void updateEnrollment(String enrollmentUid) {
        presenter.getEnrollment(enrollmentUid);
    }

    private void updateFabItems() {
        List<DialItem> dialItems = new ArrayList<>();
        dialItems.add(
                new DialItem(REFERAL_ID, getString(R.string.referral), R.drawable.ic_arrow_forward)
        );
        dialItems.add(
                new DialItem(ADD_NEW_ID, getString(R.string.add_new), R.drawable.ic_note_add)
        );
        dialItems.add(
                new DialItem(SCHEDULE_ID, getString(R.string.schedule_new),
                        R.drawable.ic_date_range)
        );
        binding.dialFabLayout.addDialItems(dialItems, clickedId -> {
            switch (clickedId) {
                case REFERAL_ID:
                    createEvent(EventCreationType.REFERAL, 0);
                    break;
                case ADD_NEW_ID:
                    createEvent(EventCreationType.ADDNEW, 0);
                    break;
                case SCHEDULE_ID:
                    createEvent(EventCreationType.SCHEDULE, 0);
                    break;
                default:
                    break;
            }
            return Unit.INSTANCE;
        });
    }

    @Override
    public void setEnrollment(Enrollment enrollment) {
        binding.setEnrollment(enrollment);
        dashboardViewModel.updateDashboard(dashboardModel);
        if (adapter != null) {
            adapter.setEnrollment(enrollment);
        }
    }

    @Override
    public void launchBiometricsVerification(String guid, String orgUnitUid) {
        BiometricsClientFactory.INSTANCE.get(context).verify(this, guid, orgUnitUid);
    }

    @Override
    public void resetVerificationStatus() {
        binding.cardFront.verificationButton.setBackground(ContextCompat.getDrawable(context,
                R.drawable.button_round_2
        ));

        binding.cardFront.verificationButtonText.setText(R.string.biometrics_verification_not_done);

        binding.cardFront.verificationButtonIcon.setImageDrawable(
                AppCompatResources.getDrawable(context, getBioIconSuccess(context)));
    }

    @Override
    public void verificationStatusMatch() {
        binding.cardFront.verificationButton.setBackground(ContextCompat.getDrawable(context,
                R.drawable.button_round_success
        ));

        binding.cardFront.verificationButtonText.setText(R.string.biometrics_verification_match);

        binding.cardFront.verificationButtonIcon.setImageDrawable(
                AppCompatResources.getDrawable(context, getBioIconSuccess(context)));
    }

    @Override
    public void verificationStatusNoMatch() {
        binding.cardFront.verificationButton.setBackground(ContextCompat.getDrawable(context,
                R.drawable.button_round_fail
        ));

        binding.cardFront.verificationButtonText.setText(R.string.biometrics_verification_no_match);

        binding.cardFront.verificationButtonIcon.setImageDrawable(
                AppCompatResources.getDrawable(context, getBioIconFailed(context)));
    }

    @Override
    public void verificationStatusFailed() {
        binding.cardFront.verificationButton.setBackground(ContextCompat.getDrawable(context,
                R.drawable.button_round_warning
        ));

        binding.cardFront.verificationButtonText.setText(R.string.biometrics_verification_failed);

        binding.cardFront.verificationButtonIcon.setImageDrawable(
                AppCompatResources.getDrawable(context, getBioIconWarning(context)));
    }

    @Override
    public void onResume() {
        super.onResume();
        presenter.init();
        dashboardViewModel.dashboardModel().observe(this, this::setData);
        dashboardViewModel.eventUid().observe(this, this::displayGenerateEvent);
    }

    @Override
    public void onPause() {
        presenter.setOpeningFilterToNone();
        presenter.onDettach();
        super.onPause();
    }

    @Override
    public void setEnrollmentData(Program program, Enrollment enrollment) {
        if (adapter != null) {
            adapter.setEnrollment(enrollment);
        }
        binding.setProgram(program);
        binding.setEnrollment(enrollment);
        if (enrollment != null) {
            followUp.set(enrollment.followUp() != null ? enrollment.followUp() : false);
        }
        binding.setFollowup(followUp);
    }

    @Override
    public void setTrackedEntityInstance(TrackedEntityInstance trackedEntityInstance,
            OrganisationUnit organisationUnit) {
        binding.setTrackEntity(trackedEntityInstance);
        binding.cardFront.orgUnit.setText(organisationUnit.displayName());
    }

    public void setData(DashboardProgramModel nprogram) {
        this.dashboardModel = nprogram;

        if (nprogram != null && nprogram.getCurrentEnrollment() != null) {
            binding.dialFabLayout.setFabVisible(true);
            presenter.setDashboardProgram(this.dashboardModel);
            SharedPreferences prefs = context.getSharedPreferences(Constants.SHARE_PREFS,
                    Context.MODE_PRIVATE);
            hasCatComb = nprogram.getCurrentProgram() != null
                    && !nprogram.getCurrentProgram().categoryComboUid().equals(
                    prefs.getString(Constants.DEFAULT_CAT_COMBO, ""));
            binding.setDashboardModel(nprogram);
            updateFabItems();
        } else if (nprogram != null) {
            binding.dialFabLayout.setFabVisible(false);
            binding.teiRecycler.setAdapter(new DashboardProgramAdapter(presenter, nprogram));
            binding.teiRecycler.addItemDecoration(
                    new DividerItemDecoration(getAbstracContext(), DividerItemDecoration.VERTICAL));
            binding.setDashboardModel(nprogram);
            showLoadingProgress(false);
        }

        binding.executePendingBindings();

        if (getSharedPreferences().getString(PREF_COMPLETED_EVENT, null) != null) {
            presenter.displayGenerateEvent(
                    getSharedPreferences().getString(PREF_COMPLETED_EVENT, null));
            getSharedPreferences().edit().remove(PREF_COMPLETED_EVENT).apply();
        }


    }

    @SuppressLint("CheckResult")
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQ_DETAILS) {
                activity.getPresenter().init();
            } else if (requestCode == BIOMETRICS_VERIFY_REQUEST) {
                onBiometricsAppResponse(data);
            }
        }
    }

    private void onBiometricsAppResponse(Intent data) {
        VerifyResult result = BiometricsClientFactory.INSTANCE.get(context).handleVerifyResponse(
                data);

        presenter.handleVerifyResponse(result);
    }

    @Override
    public void setFilters(List<FilterItem> filterItems) {
        filtersAdapter.submitList(filterItems);
    }

    @Override
    public void hideFilters() {
        activity.hideFilter();
    }

    @Override
    public Flowable<String> observeStageSelection(Program currentProgram,
            Enrollment currentEnrollment) {
        if (adapter == null) {
            adapter = new EventAdapter(presenter, currentProgram);
            adapter.setEnrollment(currentEnrollment);
            binding.teiRecycler.setAdapter(adapter);
        }
        return adapter.stageSelector();
    }

    @Override
    public void setEvents(List<EventViewModel> events, boolean canAddEvents) {

        binding.setCanAddEvents(canAddEvents);

        if (events.isEmpty()) {
            binding.emptyTeis.setVisibility(View.VISIBLE);
            if (binding.dialFabLayout.isFabVisible()) {
                binding.emptyTeis.setText(R.string.empty_tei_add);
            } else {
                binding.emptyTeis.setText(R.string.empty_tei_no_add);
            }
        } else {
            binding.emptyTeis.setVisibility(View.GONE);
            adapter.submitList(events);

            for (EventViewModel eventViewModel : events) {
                if (eventViewModel.getType() == EventViewModelType.EVENT) {
                    Event event = eventViewModel.getEvent();
                    if (event.eventDate() != null) {
                        if (event.eventDate().after(DateUtils.getInstance().getToday())) {
                            binding.teiRecycler.scrollToPosition(events.indexOf(event));
                        }
                    }
                    if (hasCatComb && event.attributeOptionCombo() == null
                            && !catComboShowed.contains(event)) {
                        presenter.getCatComboOptions(event);
                        catComboShowed.add(event);
                    } else if (!hasCatComb && event.attributeOptionCombo() == null) {
                        presenter.setDefaultCatOptCombToEvent(event.uid());
                    }
                }
            }
        }
        showLoadingProgress(false);
    }

    private void showLoadingProgress(boolean showProgress) {
        if (showProgress) {
            binding.loadingProgress.getRoot().setVisibility(View.VISIBLE);
        } else {
            binding.loadingProgress.getRoot().setVisibility(View.GONE);
        }
    }

    @Override
    public Consumer<ProgramStage> displayGenerateEvent() {
        return programStageModel -> {
            this.programStageFromEvent = programStageModel;
            if (programStageModel.displayGenerateEventBox()
                    || programStageModel.allowGenerateNextVisit()) {
                dialog = new CustomDialog(
                        getContext(),
                        getString(R.string.dialog_generate_new_event),
                        getString(R.string.message_generate_new_event),
                        getString(R.string.button_ok),
                        getString(R.string.cancel),
                        RC_GENERATE_EVENT,
                        new DialogClickListener() {
                            @Override
                            public void onPositive() {
                                createEvent(EventCreationType.SCHEDULE,
                                        programStageFromEvent.standardInterval() != null
                                                ? programStageFromEvent.standardInterval() : 0);
                            }

                            @Override
                            public void onNegative() {
                                if (programStageFromEvent.remindCompleted()) {
                                    presenter.areEventsCompleted();
                                }
                            }
                        });
                dialog.show();
            } else if (programStageModel.remindCompleted()) {
                showDialogCloseProgram();
            }
        };
    }

    private void showDialogCloseProgram() {

        dialog = new CustomDialog(
                getContext(),
                getString(R.string.event_completed),
                getString(R.string.complete_enrollment_message),
                getString(R.string.button_ok),
                getString(R.string.cancel),
                RC_EVENTS_COMPLETED,
                new DialogClickListener() {
                    @Override
                    public void onPositive() {
                        presenter.completeEnrollment();
                    }

                    @Override
                    public void onNegative() {
                    }
                });
        dialog.show();
    }

    @Override
    public Consumer<Single<Boolean>> areEventsCompleted() {
        return eventsCompleted -> {
            if (eventsCompleted.blockingGet()) {
                dialog = new CustomDialog(
                        getContext(),
                        getString(R.string.event_completed_title),
                        getString(R.string.event_completed_message),
                        getString(R.string.button_ok),
                        getString(R.string.cancel),
                        RC_EVENTS_COMPLETED,
                        new DialogClickListener() {
                            @Override
                            public void onPositive() {
                                presenter.completeEnrollment();
                            }

                            @Override
                            public void onNegative() {
                            }
                        });
                dialog.show();
            }

        };
    }

    @Override
    public Consumer<EnrollmentStatus> enrollmentCompleted() {
        return enrollmentStatus -> {
            if (enrollmentStatus == EnrollmentStatus.COMPLETED) {
                activity.updateStatus();
            }
        };
    }

    private void createEvent(EventCreationType eventCreationType, Integer scheduleIntervalDays) {
        if (isAdded()) {
            analyticsHelper().setEvent(TYPE_EVENT_TEI, eventCreationType.name(), CREATE_EVENT_TEI);
            Bundle bundle = new Bundle();
            bundle.putString(PROGRAM_UID, dashboardModel.getCurrentEnrollment().program());
            bundle.putString(TRACKED_ENTITY_INSTANCE, dashboardModel.getTei().uid());
            if (presenter.enrollmentOrgUnitInCaptureScope(
                    dashboardModel.getCurrentOrgUnit().uid())) {
                bundle.putString(ORG_UNIT, dashboardModel.getCurrentOrgUnit().uid());
            }
            bundle.putString(ENROLLMENT_UID, dashboardModel.getCurrentEnrollment().uid());
            bundle.putString(EVENT_CREATION_TYPE, eventCreationType.name());
            bundle.putInt(EVENT_SCHEDULE_INTERVAL, scheduleIntervalDays);

            bundle.putString(BIOMETRICS_GUID, dashboardModel.getTrackedBiometricEntityValue());
            bundle.putInt(BIOMETRICS_VERIFICATION_STATUS, -1);
            bundle.putString(BIOMETRICS_TEI_ORGANISATION_UNIT,
                    dashboardModel.getCurrentOrgUnit().uid());

            Intent intent = new Intent(getContext(), ProgramStageSelectionActivity.class);
            intent.putExtras(bundle);
            startActivityForResult(intent, REQ_EVENT);
        }
    }

    @Override
    public void showCatComboDialog(String eventId, Date eventDate, String categoryComboUid) {
        CategoryDialog categoryDialog = new CategoryDialog(
                CategoryDialog.Type.CATEGORY_OPTION_COMBO,
                categoryComboUid,
                true,
                eventDate,
                selectedCatOptComboUid -> {
                    presenter.changeCatOption(eventId, selectedCatOptComboUid);
                    return null;
                }
        );
        categoryDialog.setCancelable(false);
        categoryDialog.show(getChildFragmentManager(), CategoryDialog.Companion.getTAG());
    }

    @Override
    public void switchFollowUp(boolean followUp) {
        this.followUp.set(followUp);
    }

    @Override
    public void displayGenerateEvent(String eventUid) {
        if (eventUid != null) {
            presenter.displayGenerateEvent(eventUid);
            dashboardViewModel.updateEventUid(null);
        }
    }

    @Override
    public void restoreAdapter(String programUid, String teiUid, String enrollmentUid) {
        activity.startActivity(
                TeiDashboardMobileActivity.intent(activity, teiUid, programUid, enrollmentUid));
        activity.finish();
    }

    @Override
    public void seeDetails(Intent intent, Bundle bundle) {
        this.startActivityForResult(intent, REQ_DETAILS, bundle);
    }

    @Override
    public void showQR(Intent intent) {
        startActivity(intent);
    }

    @Override
    public void openEventDetails(Intent intent, Bundle bundle) {
        this.startActivityForResult(intent, REQ_EVENT, bundle);
    }

    @Override
    public void openEventInitial(Intent intent) {
        this.startActivityForResult(intent, REQ_EVENT, null);
    }

    @Override
    public void openEventCapture(Intent intent) {
        this.startActivityForResult(intent, REQ_EVENT, null);
    }

    @Override
    public void showTeiImage(String filePath, String defaultIcon) {
        if (filePath.isEmpty() && defaultIcon.isEmpty()) {
            binding.cardFront.teiImage.setVisibility(View.GONE);
        } else {
            binding.cardFront.teiImage.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(new File(filePath))
                    .error(
                            ObjectStyleUtils.getIconResource(context, defaultIcon,
                                    R.drawable.photo_temp_gray)
                    )
                    .transition(withCrossFade())
                    .transform(new CircleCrop())
                    .into(binding.cardFront.teiImage);
            binding.cardFront.teiImage.setOnClickListener(view -> {
                File fileToShow = new File(filePath);
                if (fileToShow.exists()) {
                    new ImageDetailBottomDialog(
                            null,
                            fileToShow
                    ).show(getChildFragmentManager(), ImageDetailBottomDialog.TAG);
                }
            });
        }
    }

    @Override
    public void showNewEventOptions(View anchor, ProgramStage stage) {
        popupMenu = new PopupMenu(context, anchor);
        popupMenu.inflate(R.menu.dashboard_event_creation);

        popupMenu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.schedulenew:
                    goToEventInitial(EventCreationType.SCHEDULE, stage);
                    break;
                case R.id.addnew:
                    goToEventInitial(EventCreationType.ADDNEW, stage);
                    break;
                case R.id.referral:
                    goToEventInitial(EventCreationType.REFERAL, stage);
                    break;
            }
            return true;
        });
        popupMenu.show();

    }

    @Override
    public void hideDueDate() {
        popupMenu.getMenu().findItem(R.id.schedulenew).setVisible(false);
    }

    private void goToEventInitial(EventCreationType eventCreationType, ProgramStage programStage) {
        Intent intent = new Intent(activity, EventInitialActivity.class);
        Bundle bundle = new Bundle();
        bundle.putString(PROGRAM_UID, dashboardModel.getCurrentProgram().uid());
        bundle.putString(TRACKED_ENTITY_INSTANCE, dashboardModel.getTei().uid());
        if (presenter.enrollmentOrgUnitInCaptureScope(dashboardModel.getCurrentOrgUnit().uid())) {
            bundle.putString(ORG_UNIT, dashboardModel.getCurrentOrgUnit().uid());
        }
        bundle.putString(ENROLLMENT_UID, dashboardModel.getCurrentEnrollment().uid());
        bundle.putString(EVENT_CREATION_TYPE, eventCreationType.name());
        bundle.putBoolean(EVENT_REPEATABLE, programStage.repeatable());
        bundle.putSerializable(EVENT_PERIOD_TYPE, programStage.periodType());
        bundle.putString(Constants.PROGRAM_STAGE_UID, programStage.uid());
        bundle.putInt(EVENT_SCHEDULE_INTERVAL,
                programStage.standardInterval() != null ? programStage.standardInterval() : 0);

        bundle.putString(BIOMETRICS_GUID, dashboardModel.getTrackedBiometricEntityValue());
        bundle.putInt(BIOMETRICS_VERIFICATION_STATUS, -1);
        bundle.putString(BIOMETRICS_TEI_ORGANISATION_UNIT,
                dashboardModel.getCurrentOrgUnit().uid());

        intent.putExtras(bundle);
        startActivityForResult(intent, REQ_EVENT);
    }

    private void showHideFilters(boolean showFilters) {
        if (showFilters) {
            binding.teiData.setVisibility(View.GONE);
            binding.filterLayout.setVisibility(View.VISIBLE);
        } else {
            binding.teiData.setVisibility(View.VISIBLE);
            binding.filterLayout.setVisibility(View.GONE);
        }
    }

    @Override
    public void showPeriodRequest(FilterManager.PeriodRequest periodRequest) {
        if (periodRequest == FilterManager.PeriodRequest.FROM_TO) {
            DateUtils.getInstance().fromCalendarSelector(
                    activity,
                    FilterManager.getInstance()::addPeriod);
        } else {
            DateUtils.getInstance().showPeriodDialog(
                    activity,
                    FilterManager.getInstance()::addPeriod,
                    true);
        }
    }

    @Override
    public void openOrgUnitTreeSelector(String programUid) {
        OUTreeFragment ouTreeFragment = OUTreeFragment.Companion.newInstance(true,
                FilterManager.getInstance().getOrgUnitUidsFilters());
        ouTreeFragment.setSelectionCallback(this);
        ouTreeFragment.show(getChildFragmentManager(), "OUTreeFragment");
    }

    @Override
    public void showSyncDialog(String uid) {
        SyncStatusDialog dialog = new SyncStatusDialog.Builder()
                .setConflictType(SyncStatusDialog.ConflictType.TEI)
                .setUid(uid)
                .onDismissListener(hasChanged -> {
                    if (hasChanged) {
                        FilterManager.getInstance().publishData();
                    }

                })
                .build();

        dialog.show(getChildFragmentManager(), uid);
    }

    @Override
    public void onSelectionFinished(@NotNull List<? extends OrganisationUnit> selectedOrgUnits) {
        presenter.setOrgUnitFilters((List<OrganisationUnit>) selectedOrgUnits);
    }
}