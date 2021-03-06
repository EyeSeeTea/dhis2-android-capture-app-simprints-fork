package org.dhis2.data.forms.dataentry;

import androidx.annotation.NonNull;

import org.dhis2.form.model.FieldUiModel;
import org.hisp.dhis.android.core.organisationunit.OrganisationUnit;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Observable;

public interface DataEntryRepository {

    @NonNull
    Flowable<List<FieldUiModel>> list();

    Observable<List<OrganisationUnit>> getOrgUnits();

    @NotNull Flowable<List<String>> enrollmentSectionUids();
}
