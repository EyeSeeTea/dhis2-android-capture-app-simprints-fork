package org.dhis2.usescases.teiDashboard.teiProgramList;

import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.databinding.ViewDataBinding;
import androidx.recyclerview.widget.RecyclerView;

import org.dhis2.BR;
import org.dhis2.R;
import org.dhis2.databinding.ItemTeiProgramsEnrollmentBinding;
import org.dhis2.databinding.ItemTeiProgramsEnrollmentInactiveBinding;
import org.dhis2.databinding.ItemTeiProgramsProgramsBinding;
import org.dhis2.usescases.main.program.ProgramViewModel;
import org.dhis2.commons.resources.ColorUtils;
import org.dhis2.commons.resources.ResourceManager;

import timber.log.Timber;

/**
 * QUADRAM. Created by Cristian on 13/02/2018.
 */

public class TeiProgramListEnrollmentViewHolder extends RecyclerView.ViewHolder {

    private ViewDataBinding binding;

    TeiProgramListEnrollmentViewHolder(ViewDataBinding binding) {
        super(binding.getRoot());
        this.binding = binding;
    }

    public void bind(TeiProgramListContract.Presenter presenter, EnrollmentViewModel enrollment, ProgramViewModel programModel) {
        binding.setVariable(BR.enrollment, enrollment);
        binding.setVariable(BR.program, programModel);
        binding.setVariable(BR.presenter, presenter);

        if (enrollment != null) {

            ImageView programImage;
            RelativeLayout iconBg;
            if (binding instanceof ItemTeiProgramsEnrollmentBinding) {
                programImage = ((ItemTeiProgramsEnrollmentBinding) binding).programImage;
                iconBg = ((ItemTeiProgramsEnrollmentBinding) binding).iconBg;
            } else {
                programImage = ((ItemTeiProgramsEnrollmentInactiveBinding) binding).programImage;
                iconBg = ((ItemTeiProgramsEnrollmentInactiveBinding) binding).iconBg;
            }

            int color = ColorUtils.getColorFrom(enrollment.color(),
                    ColorUtils.getPrimaryColor(itemView.getContext(), ColorUtils.ColorType.PRIMARY));
            int icon = new ResourceManager(itemView.getContext())
                    .getObjectStyleDrawableResource(
                            enrollment.icon(),
                            R.drawable.ic_default_outline
                    );

            Drawable iconImage = null;
            try {
                iconImage = AppCompatResources.getDrawable(itemView.getContext(), icon);
            } catch (Exception e) {
                Timber.log(1, e);
            }

            if (iconImage != null) {
                programImage.setImageDrawable(ColorUtils.tintDrawableReosurce(iconImage, color));
            }

            Drawable bgImage = AppCompatResources.getDrawable(itemView.getContext(), R.drawable.photo_temp_gray);
            if (bgImage != null) {
                iconBg.setBackground(ColorUtils.tintDrawableWithColor(bgImage, color));
            }
        } else if (programModel != null) {
            ImageView programImage;
            RelativeLayout iconBg;
            if (binding instanceof ItemTeiProgramsProgramsBinding) {
                programImage = ((ItemTeiProgramsProgramsBinding) binding).programImage;
                iconBg = ((ItemTeiProgramsProgramsBinding) binding).iconBg;

            } else {
                programImage = ((ItemTeiProgramsEnrollmentInactiveBinding) binding).programImage;
                iconBg = ((ItemTeiProgramsEnrollmentInactiveBinding) binding).iconBg;

            }

            int color = ColorUtils.getColorFrom(programModel.color(),
                    ColorUtils.getPrimaryColor(itemView.getContext(), ColorUtils.ColorType.PRIMARY));
            int icon = new ResourceManager(itemView.getContext())
                    .getObjectStyleDrawableResource(
                            programModel.icon(),
                            R.drawable.ic_default_outline
                    );

            Drawable iconImage = null;
            try {
                iconImage = AppCompatResources.getDrawable(itemView.getContext(), icon);
            } catch (Exception e) {
                Timber.log(1, e);
            }

            if (iconImage != null) {
                programImage.setImageDrawable(ColorUtils.tintDrawableReosurce(iconImage, color));
            }

            Drawable bgImage = AppCompatResources.getDrawable(itemView.getContext(), R.drawable.photo_temp_gray);
            if (bgImage != null) {
                iconBg.setBackground(ColorUtils.tintDrawableWithColor(bgImage, color));
            }
        }

        binding.executePendingBindings();
    }
}