package org.dhis2.usescases.teiDashboard;

import com.google.gson.reflect.TypeToken;

import org.dhis2.commons.prefs.Preference;
import org.dhis2.commons.prefs.PreferenceProvider;
import org.dhis2.commons.schedulers.SchedulerProvider;
import org.dhis2.utils.AuthorityException;
import org.dhis2.utils.Constants;
import org.dhis2.utils.analytics.AnalyticsHelper;
import org.dhis2.commons.filters.FilterManager;
import org.hisp.dhis.android.core.common.Unit;
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus;
import org.hisp.dhis.android.core.program.Program;

import java.util.HashMap;
import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.processors.PublishProcessor;
import timber.log.Timber;

import static org.dhis2.utils.analytics.AnalyticsConstants.CLICK;
import static org.dhis2.utils.analytics.AnalyticsConstants.DELETE_ENROLL;
import static org.dhis2.utils.analytics.AnalyticsConstants.DELETE_TEI;

public class TeiDashboardPresenter implements TeiDashboardContracts.Presenter {

    private final DashboardRepository dashboardRepository;
    private final SchedulerProvider schedulerProvider;
    private final AnalyticsHelper analyticsHelper;
    private final PreferenceProvider preferenceProvider;
    private final FilterManager filterManager;
    private final TeiDashboardContracts.View view;

    private String teiUid;
    public String programUid;

    public CompositeDisposable compositeDisposable;
    public DashboardProgramModel dashboardProgramModel;
    private PublishProcessor<Unit> notesCounterProcessor;


    public TeiDashboardPresenter(
            TeiDashboardContracts.View view,
            String teiUid, String programUid, String enrollmentUid,
            DashboardRepository dashboardRepository,
            SchedulerProvider schedulerProvider,
            AnalyticsHelper analyticsHelper,
            PreferenceProvider preferenceProvider,
            FilterManager filterManager
    ) {
        this.view = view;
        this.teiUid = teiUid;
        this.programUid = programUid;
        this.analyticsHelper = analyticsHelper;
        this.dashboardRepository = dashboardRepository;
        this.schedulerProvider = schedulerProvider;
        this.preferenceProvider = preferenceProvider;
        this.filterManager = filterManager;
        compositeDisposable = new CompositeDisposable();
        notesCounterProcessor = PublishProcessor.create();
    }

    @Override
    public void init() {
        if (programUid != null)
            compositeDisposable.add(Observable.zip(
                    dashboardRepository.getTrackedEntityInstance(teiUid),
                    dashboardRepository.getEnrollment(),
                    dashboardRepository.getProgramStages(programUid),
                    dashboardRepository.getTEIEnrollmentEvents(programUid, teiUid),
                    dashboardRepository.getProgramTrackedEntityAttributes(programUid),
                    dashboardRepository.getTEIAttributeValues(programUid, teiUid),
                    dashboardRepository.getTeiOrgUnits(teiUid, programUid),
                    dashboardRepository.getTeiActivePrograms(teiUid, false),
                    DashboardProgramModel::new)
                    .subscribeOn(schedulerProvider.io())
                    .observeOn(schedulerProvider.ui())
                    .subscribe(
                            dashboardModel -> {
                                this.dashboardProgramModel = dashboardModel;
                                view.setData(dashboardModel);
                            },
                            Timber::e
                    )
            );

        else {
            compositeDisposable.add(Observable.zip(
                    dashboardRepository.getTrackedEntityInstance(teiUid),
                    dashboardRepository.getProgramTrackedEntityAttributes(null),
                    dashboardRepository.getTEIAttributeValues(null, teiUid),
                    dashboardRepository.getTeiOrgUnits(teiUid, null),
                    dashboardRepository.getTeiActivePrograms(teiUid, true),
                    dashboardRepository.getTEIEnrollments(teiUid),
                    DashboardProgramModel::new)
                    .subscribeOn(schedulerProvider.io())
                    .observeOn(schedulerProvider.ui())
                    .subscribe(
                            dashboardModel -> {
                                this.dashboardProgramModel = dashboardModel;
                                view.setDataWithOutProgram(dashboardProgramModel);
                            },
                            Timber::e)
            );
        }
        setTotalFilters();
    }

    @Override
    public void setTotalFilters() {
        compositeDisposable.add(
                filterManager.asFlowable()
                        .startWith(filterManager)
                        .map(FilterManager::getTotalFilters)
                        .subscribeOn(schedulerProvider.io())
                        .observeOn(schedulerProvider.ui())
                        .subscribe(
                                totalFilters -> view.updateTotalFilters(totalFilters),
                                Timber::e
                        )
        );
    }

    @Override
    public void onEnrollmentSelectorClick() {
        view.goToEnrollmentList();
    }

    @Override
    public void setProgram(Program program) {
        this.programUid = program.uid();
        view.restoreAdapter(programUid);
        init();
    }

    @Override
    public void deleteTei() {
        compositeDisposable.add(
                dashboardRepository.deleteTeiIfPossible()
                        .subscribeOn(schedulerProvider.io())
                        .observeOn(schedulerProvider.ui())
                        .subscribe(
                                canDelete -> {
                                    if (canDelete) {
                                        analyticsHelper.setEvent(DELETE_TEI, CLICK, DELETE_TEI);
                                        view.handleTeiDeletion();
                                    } else {
                                        view.authorityErrorMessage();
                                    }
                                },
                                Timber::e
                        )
        );
    }

    @Override
    public void deleteEnrollment() {
        compositeDisposable.add(
                dashboardRepository.deleteEnrollmentIfPossible(
                        dashboardProgramModel.getCurrentEnrollment().uid()
                )
                        .subscribeOn(schedulerProvider.io())
                        .observeOn(schedulerProvider.ui())
                        .subscribe(
                                hasMoreEnrollments -> {
                                    analyticsHelper.setEvent(DELETE_ENROLL, CLICK, DELETE_ENROLL);
                                    view.handleEnrollmentDeletion(hasMoreEnrollments);
                                },
                                error -> {
                                    if (error instanceof AuthorityException)
                                        view.authorityErrorMessage();
                                    else
                                        Timber.e(error);
                                }
                        )
        );
    }

    @Override
    public void onDettach() {
        compositeDisposable.clear();
    }

    @Override
    public void onBackPressed() {
        view.back();
    }

    @Override
    public String getProgramUid() {
        return programUid;
    }

    @Override
    public void showDescription(String description) {
        view.showDescription(description);
    }

    @Override
    public void initNoteCounter() {
        if (!notesCounterProcessor.hasSubscribers()) {
            compositeDisposable.add(
                    notesCounterProcessor.startWith(new Unit())
                            .flatMapSingle(unit ->
                                    dashboardRepository.getNoteCount())
                            .subscribeOn(schedulerProvider.io())
                            .observeOn(schedulerProvider.ui())
                            .subscribe(
                                    numberOfNotes ->
                                            view.updateNoteBadge(numberOfNotes),
                                    Timber::e
                            )
            );
        } else {
            notesCounterProcessor.onNext(new Unit());
        }
    }

    @Override
    public void refreshTabCounters() {
        initNoteCounter();
    }

    @Override
    public int getProgramTheme(int appTheme) {
        return preferenceProvider.getInt(Constants.PROGRAM_THEME, preferenceProvider.getInt(Constants.THEME, appTheme));
    }

    @Override
    public void prefSaveCurrentProgram(String programUid) {
        preferenceProvider.setValue(Constants.PREVIOUS_DASHBOARD_PROGRAM, programUid);
    }

    @Override
    public void saveProgramTheme(int programTheme) {
        preferenceProvider.setValue(Constants.PROGRAM_THEME, programTheme);
    }

    @Override
    public void removeProgramTheme() {
        preferenceProvider.removeValue(Constants.PROGRAM_THEME);
    }

    @Override
    public Boolean getProgramGrouping() {
        if (programUid != null) {
            return getGrouping().containsKey(programUid) ? getGrouping().get(programUid) : true;
        } else {
            return false;
        }
    }

    @Override
    public void generalFiltersClick() {
        view.setFiltersLayoutState();
    }

    @Override
    public void handleShowHideFilters(boolean showFilters) {
        if (showFilters) {
            view.hideTabsAndDisableSwipe();
        } else {
            view.showTabsAndEnableSwipe();
        }
    }

    @Override
    public EnrollmentStatus getEnrollmentStatus(String enrollmentUid) {
        return dashboardRepository.getEnrollmentStatus(enrollmentUid);
    }

    @Override
    public void updateEnrollmentStatus(String enrollmentUid, EnrollmentStatus status) {
        compositeDisposable.add(
                dashboardRepository.updateEnrollmentStatus(enrollmentUid, status)
                        .subscribeOn(schedulerProvider.io())
                        .observeOn(schedulerProvider.ui())
                        .subscribe(statusCode -> {
                            if (statusCode == StatusChangeResultCode.CHANGED) {
                                view.updateStatus();
                            } else {
                                view.displayStatusError(statusCode);
                            }
                        }, Timber::e)
        );
    }


    private Map<String, Boolean> getGrouping() {
        TypeToken<HashMap<String, Boolean>> typeToken =
                new TypeToken<HashMap<String, Boolean>>() {
                };
        return preferenceProvider.getObjectFromJson(
                Preference.GROUPING,
                typeToken,
                new HashMap<>());
    }
}
