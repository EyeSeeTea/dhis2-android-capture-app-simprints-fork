package org.dhis2.usescases.eventsWithoutRegistration.eventCapture.eventCaptureFragment

import dagger.Subcomponent
import org.dhis2.commons.di.dagger.PerFragment

@PerFragment
@Subcomponent(modules = [EventCaptureFormModule::class])
interface EventCaptureFormComponent {
    fun inject(fragment: EventCaptureFormFragment)
}
