package org.dhis2.data.forms.dataentry.fields.biometricsVerification;

import com.google.auto.value.AutoValue;

import org.dhis2.R;
import org.dhis2.data.forms.dataentry.DataEntryViewHolderTypes;
import org.dhis2.data.forms.dataentry.fields.FieldViewModel;
import org.dhis2.form.model.RowAction;
import org.dhis2.form.ui.style.FormUiModelStyle;
import org.hisp.dhis.android.core.common.ObjectStyle;

import javax.annotation.Nonnull;

import androidx.annotation.NonNull;
import io.reactivex.processors.FlowableProcessor;

@AutoValue
public abstract class BiometricsVerificationViewModel extends FieldViewModel {

    @NonNull
    public abstract BiometricsVerificationView.BiometricsVerificationStatus status();

    public static FieldViewModel create(String id, String label, Boolean mandatory, String value, String section, Boolean editable, String description, ObjectStyle objectStyle, FlowableProcessor<RowAction> processor, FormUiModelStyle style,String url, BiometricsVerificationView.BiometricsVerificationStatus status) {
        return new AutoValue_BiometricsVerificationViewModel(id, label, mandatory, value, section, null, editable, null, null, null, description, objectStyle, null, DataEntryViewHolderTypes.BIOMETRICS_VERIFICATION, processor, style,false, url, status);
    }

    @Override
    public FieldViewModel setMandatory() {
        return new AutoValue_BiometricsVerificationViewModel(uid(), label(), true,value(),  programStageSection(), allowFutureDate(), editable(), optionSet(), warning(), error(), description(), objectStyle(), null, DataEntryViewHolderTypes.BIOMETRICS_VERIFICATION, processor(), style(), activated(), url(), status());
    }

    @NonNull
    @Override
    public FieldViewModel withError(@NonNull String error) {
        return new AutoValue_BiometricsVerificationViewModel(uid(), label(), mandatory(),value(),  programStageSection(), allowFutureDate(), editable(), optionSet(), warning(), error, description(), objectStyle(), null, DataEntryViewHolderTypes.BIOMETRICS_VERIFICATION, processor(), style(), activated(), url(), status());
    }

    @NonNull
    @Override
    public FieldViewModel withWarning(@NonNull String warning) {
        return new AutoValue_BiometricsVerificationViewModel(uid(), label(), mandatory(),value(),  programStageSection(), allowFutureDate(), editable(), optionSet(), warning, error(), description(), objectStyle(), null, DataEntryViewHolderTypes.BIOMETRICS_VERIFICATION, processor(), style(), activated(), url(), status());
    }

    @NonNull
    @Override
    public FieldViewModel withValue(String value) {
        return new AutoValue_BiometricsVerificationViewModel(uid(), label(), mandatory(),value,  programStageSection(), allowFutureDate(), editable(), optionSet(), warning(), error(), description(), objectStyle(), null, DataEntryViewHolderTypes.BIOMETRICS_VERIFICATION, processor(), style(), activated(), url(), status());
    }

    @NonNull
    @Override
    public FieldViewModel withEditMode(boolean isEditable) {
        return new AutoValue_BiometricsVerificationViewModel(uid(), label(), mandatory(),value(),  programStageSection(), allowFutureDate(), isEditable, optionSet(), warning(), error(), description(), objectStyle(), null, DataEntryViewHolderTypes.BIOMETRICS_VERIFICATION, processor(), style(), activated(), url(), status());
    }

    @NonNull
    @Override
    public FieldViewModel withFocus(boolean isFocused) {
        return new AutoValue_BiometricsVerificationViewModel(uid(), label(), mandatory(),value(),  programStageSection(), allowFutureDate(), editable(), optionSet(), warning(), error(), description(), objectStyle(), null, DataEntryViewHolderTypes.BIOMETRICS_VERIFICATION, processor(), style(), isFocused, url(), status());
    }

    @Nonnull
    public FieldViewModel withValueAndStatus(String data,  BiometricsVerificationView.BiometricsVerificationStatus status){
        return new AutoValue_BiometricsVerificationViewModel(uid(), label(), mandatory(),data, programStageSection(), allowFutureDate(), editable(), optionSet(), warning(), error(), description(), objectStyle(), null, DataEntryViewHolderTypes.BIOMETRICS_VERIFICATION, processor(), style(), activated(), url(), status);
    }

    @Override
    public int getLayoutId() {
        return R.layout.form_biometrics_verification;
    }
}