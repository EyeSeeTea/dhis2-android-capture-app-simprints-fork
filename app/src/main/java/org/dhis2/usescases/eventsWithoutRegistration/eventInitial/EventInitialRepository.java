package org.dhis2.usescases.eventsWithoutRegistration.eventInitial;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.dhis2.data.forms.FormSectionViewModel;
import org.dhis2.data.forms.dataentry.fields.coordinate.CoordinateViewModel;
import org.dhis2.form.model.FieldUiModel;
import org.dhis2.form.model.RowAction;
import org.dhis2.utils.Result;
import org.hisp.dhis.android.core.category.CategoryCombo;
import org.hisp.dhis.android.core.category.CategoryOption;
import org.hisp.dhis.android.core.category.CategoryOptionCombo;
import org.hisp.dhis.android.core.common.Geometry;
import org.hisp.dhis.android.core.common.ObjectStyle;
import org.hisp.dhis.android.core.event.Event;
import org.hisp.dhis.android.core.event.EventEditableStatus;
import org.hisp.dhis.android.core.organisationunit.OrganisationUnit;
import org.hisp.dhis.android.core.program.Program;
import org.hisp.dhis.android.core.program.ProgramStage;
import org.hisp.dhis.rules.models.RuleEffect;

import java.util.Date;
import java.util.List;
import java.util.Map;

import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.processors.FlowableProcessor;

public interface EventInitialRepository {

    @NonNull
    Observable<Event> event(String eventId);

    @NonNull
    Observable<CategoryCombo> catCombo(String programUid);

    @NonNull
    Observable<List<OrganisationUnit>> orgUnits(String programId);

    @NonNull
    Observable<List<OrganisationUnit>> filteredOrgUnits(String date, String programId, String parentId);

    Observable<String> createEvent(String enrollmentUid, @Nullable String trackedEntityInstanceUid,
                                   @NonNull String program,
                                   @NonNull String programStage, @NonNull Date date,
                                   @NonNull String orgUnitUid, @NonNull String catComboUid,
                                   @NonNull String catOptionUid, @NonNull Geometry coordinates);

    Observable<String> scheduleEvent(String enrollmentUid, @Nullable String trackedEntityInstanceUid,
                                     @NonNull String program,
                                     @NonNull String programStage, @NonNull Date dueDate,
                                     @NonNull String orgUnitUid, @NonNull String catComboUid,
                                     @NonNull String catOptionUid, @NonNull Geometry coordinates);

    @NonNull
    Observable<ProgramStage> programStage(String programUid);

    @NonNull
    Observable<ProgramStage> programStageWithId(String programStageUid);

    @NonNull
    Observable<Event> editEvent(String trackedEntityInstance, String eventUid, String date, String orgUnitUid, String catComboUid, String catOptionCombo, Geometry coordinates);

    Observable<Boolean> accessDataWrite(String programId);

    void deleteEvent(String eventId, String trackedEntityInstance);

    boolean isEnrollmentOpen();

    Observable<List<CategoryOptionCombo>> catOptionCombos(String catOptionComboUid);

    Flowable<Map<String, CategoryOption>> getOptionsFromCatOptionCombo(String eventId);

    Date getStageLastDate(String programStageUid, String enrollmentUid);

    Observable<Program> getProgramWithId(String programUid);

    Flowable<ProgramStage> programStageForEvent(String eventId);

    Observable<OrganisationUnit> getOrganisationUnit(String orgUnitUid);

    Observable<ObjectStyle> getObjectStyle(String uid);

    String getCategoryOptionCombo(String categoryComboUid, List<String> categoryOptionsUid);

    CategoryOption getCatOption(String selectedOption);

    int getCatOptionSize(String uid);

    List<CategoryOption> getCategoryOptions(String categoryUid);

    boolean showCompletionPercentage();

    Flowable<List<FormSectionViewModel>> eventSections();

    Flowable<List<FieldUiModel>> list();

    Flowable<Result<RuleEffect>> calculate();

    Single<CoordinateViewModel> getGeometryModel(String programUid, FlowableProcessor<RowAction> processor);

    Flowable<EventEditableStatus> getEditableStatus();

    int getMinDaysFromStartByProgramStage(String programStageUid);
}
