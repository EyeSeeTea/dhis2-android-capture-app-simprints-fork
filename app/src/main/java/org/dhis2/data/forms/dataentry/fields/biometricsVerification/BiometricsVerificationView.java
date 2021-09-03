package org.dhis2.data.forms.dataentry.fields.biometricsVerification;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.dhis2.R;
import org.dhis2.data.biometrics.BiometricsClientFactory;
import org.dhis2.databinding.AgeCustomViewBinding;
import org.dhis2.databinding.BiometricsVerificationViewBinding;
import org.dhis2.databinding.BiometricsViewBinding;
import org.dhis2.utils.customviews.FieldLayout;

import androidx.appcompat.content.res.AppCompatResources;

public class BiometricsVerificationView extends FieldLayout {

    private BiometricsVerificationViewBinding binding;

    private BiometricsVerificationViewModel viewModel;

    public enum BiometricsVerificationStatus {
        NOT_DONE,
        SUCCESS,
        FAILURE
    }

    //private String guid = null;

    LinearLayout tryAgainButton;
    ImageView statusImageView;

    public BiometricsVerificationView(Context context) {
        super(context);
        init(context);
    }

    public BiometricsVerificationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public BiometricsVerificationView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public void init(Context context) {
        super.init(context);
        inflater = LayoutInflater.from(context);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setLayout() {
        binding = BiometricsVerificationViewBinding.inflate(inflater, this, true);

        tryAgainButton = findViewById(R.id.tryAgainButton);
        statusImageView  = findViewById(R.id.statusImageView);

        tryAgainButton.setOnClickListener(v -> {
            if (viewModel != null) {
                viewModel.onItemClick();
            }
        });
    }

    public void setViewModel(BiometricsVerificationViewModel viewModel) {
        this.viewModel = viewModel;

        if (binding == null) {
            setLayout();
        }

        //guid = viewModel.value();

        if(viewModel.status().equals(BiometricsVerificationStatus.SUCCESS)){
            statusImageView.setImageDrawable(AppCompatResources.getDrawable(getContext(), R.drawable.ic_bio_available_yes));
            tryAgainButton.setVisibility(GONE);
        }else if(viewModel.status().equals(BiometricsVerificationStatus.FAILURE)){
            statusImageView.setImageDrawable(AppCompatResources.getDrawable(getContext(), R.drawable.ic_bio_available_no));
            tryAgainButton.setVisibility(VISIBLE);
        }else{
            statusImageView.setImageDrawable(AppCompatResources.getDrawable(getContext(), R.drawable.ic_bio_available_no));
            tryAgainButton.setVisibility(GONE);
        }
    }


 /*   private void launchBiometricsVerification() {
        BiometricsClientFactory.INSTANCE.get(this.getContext()).verify(((Activity) this.getContext()), guid);
    }*/
}