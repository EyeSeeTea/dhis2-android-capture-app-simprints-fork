package org.dhis2.usescases.programEventDetail

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.paging.PagedList
import com.mapbox.geojson.BoundingBox
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.schedulers.Schedulers
import junit.framework.Assert.assertTrue
import org.dhis2.data.filter.FilterPresenter
import org.dhis2.data.filter.FilterRepository
import org.dhis2.data.schedulers.TrampolineSchedulerProvider
import org.dhis2.data.tuples.Pair
import org.dhis2.usescases.teiDashboard.dashboardfragments.teidata.teievents.EventViewModel
import org.dhis2.usescases.teiDashboard.dashboardfragments.teidata.teievents.EventViewModelType
import org.dhis2.utils.analytics.matomo.MatomoAnalyticsController
import org.dhis2.utils.filters.DisableHomeFiltersFromSettingsApp
import org.dhis2.utils.filters.FilterItem
import org.dhis2.utils.filters.FilterManager
import org.dhis2.utils.filters.Filters
import org.dhis2.utils.filters.sorting.SortingItem
import org.dhis2.utils.filters.sorting.SortingStatus
import org.dhis2.utils.filters.workingLists.EventFilterToWorkingListItemMapper
import org.hisp.dhis.android.core.category.CategoryCombo
import org.hisp.dhis.android.core.category.CategoryOptionCombo
import org.hisp.dhis.android.core.common.FeatureType
import org.hisp.dhis.android.core.dataelement.DataElement
import org.hisp.dhis.android.core.event.Event
import org.hisp.dhis.android.core.program.Program
import org.hisp.dhis.android.core.program.ProgramStage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ProgramEventDetailPresenterTest {
    @Rule
    @JvmField
    var instantExecutorRule = InstantTaskExecutorRule()

    private val filterRepository: FilterRepository = mock()
    private lateinit var presenter: ProgramEventDetailPresenter

    private val view: ProgramEventDetailContract.View = mock()
    private val repository: ProgramEventDetailRepository = mock()
    private val scheduler = TrampolineSchedulerProvider()
    private val filterManager: FilterManager = FilterManager.getInstance()
    private val workingListMapper: EventFilterToWorkingListItemMapper = mock()
    private val filterPresenter: FilterPresenter = mock()
    private val disableHomeFilters: DisableHomeFiltersFromSettingsApp = mock()
    private val matomoAnalyticsController: MatomoAnalyticsController = mock()

    @Before
    fun setUp() {
        RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }

        presenter = ProgramEventDetailPresenter(
            view,
            repository,
            scheduler,
            filterManager,
            workingListMapper,
            filterRepository,
            filterPresenter,
            disableHomeFilters,
            matomoAnalyticsController
        )
    }

    @After
    fun clear() {
        FilterManager.getInstance().clearAllFilters()
        RxAndroidPlugins.reset()
    }

    @Test
    fun `Should init screen`() {
        val program = Program.builder().uid("programUid").build()
        val catOptionComboPair = Pair.create(dummyCategoryCombo(), dummyListCatOptionCombo())

        val eventViewModel = EventViewModel(
            EventViewModelType.EVENT,
            ProgramStage.builder().uid("stageUid").build(),
            Event.builder().uid("event").build(),
            eventCount = 0,
            lastUpdate = null,
            isSelected = false,
            canAddNewEvent = true,
            orgUnitName = "orgUnit",
            catComboName = "catComboName",
            dataElementValues = emptyList(),
            groupedByStage = false,
            valueListIsOpen = false,
            displayDate = "2/01/2021"
        )
        val events =
            MutableLiveData<PagedList<EventViewModel>>().also {
                it.value?.add(eventViewModel)
            }

        val mapEvents = Triple<FeatureCollection, BoundingBox, List<ProgramEventViewModel>>(
            FeatureCollection.fromFeature(Feature.fromGeometry(null)),
            BoundingBox.fromLngLats(0.0, 0.0, 0.0, 0.0),
            listOf()
        )
        val mapData = ProgramEventMapData(
            mutableListOf(),
            mutableMapOf("key" to FeatureCollection.fromFeature(Feature.fromGeometry(null))),
            BoundingBox.fromLngLats(0.0, 0.0, 0.0, 0.0)
        )
        filterManager.sortingItem = SortingItem(Filters.ORG_UNIT, SortingStatus.NONE)
        whenever(repository.featureType()) doReturn Single.just(FeatureType.POINT)
        whenever(repository.accessDataWrite) doReturn true
        whenever(repository.hasAccessToAllCatOptions()) doReturn Single.just(true)
        whenever(repository.program()) doReturn Observable.just(program)
        whenever(
            repository.filteredProgramEvents(null)
        ) doReturn events
        whenever(
            repository.filteredEventsForMap()
        ) doReturn Flowable.just(mapData)
        presenter.init()
        verify(view).setFeatureType(FeatureType.POINT)
        verify(view).setWritePermission(true)
        verify(view).setProgram(program)
    }

    @Test
    fun `Should show sync dialog`() {
        presenter.onSyncIconClick("uid")

        verify(view).showSyncDialog("uid")
    }

    @Test
    fun `Should start new event`() {
        presenter.addEvent()

        verify(view).startNewEvent()
    }

    @Test
    fun `Should go back when back button is pressed`() {
        presenter.onBackClick()

        verify(view).back()
    }

    @Test
    fun `Should dispose of all disposables`() {
        presenter.onDettach()

        val result = presenter.compositeDisposable.size()

        assert(result == 0)
    }

    @Test
    fun `Should display message`() {
        val message = "message"

        presenter.displayMessage(message)

        verify(view).displayMessage(message)
    }

    @Test
    fun `Should show or hide filter`() {
        presenter.showFilter()

        verify(view).showHideFilter()
    }

    @Test
    fun `Should clear all filters when reset filter button is clicked`() {
        presenter.clearFilterClick()
        assertTrue(filterManager.totalFilters == 0)
    }

    @Test
    fun `Should clear other filters if webapp is config`() {
        val list = listOf<FilterItem>()
        whenever(filterRepository.homeFilters()) doReturn listOf()

        presenter.clearOtherFiltersIfWebAppIsConfig()

        verify(disableHomeFilters).execute(list)
    }

    private fun dummyCategoryCombo() = CategoryCombo.builder().uid("uid").build()

    private fun dummyListCatOptionCombo(): List<CategoryOptionCombo> =
        listOf(CategoryOptionCombo.builder().uid("uid").build())
}