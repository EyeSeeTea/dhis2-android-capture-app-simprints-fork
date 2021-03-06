package org.dhis2.usescases.enrollment

import dagger.Subcomponent
import org.dhis2.commons.di.dagger.PerActivity

@PerActivity
@Subcomponent(modules = [EnrollmentModule::class])
interface EnrollmentComponent {
    fun inject(activity: EnrollmentActivity)
}
