package org.dhis2.usescases.datasets.dataSetTable.dataSetSection

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.dhis2.commons.prefs.PreferenceProvider
import org.dhis2.commons.schedulers.SchedulerProvider
import org.dhis2.data.forms.dataentry.ValueStore
import org.dhis2.data.forms.dataentry.tablefields.FieldViewModel
import org.dhis2.data.forms.dataentry.tablefields.edittext.EditTextViewModel
import org.dhis2.data.schedulers.TrampolineSchedulerProvider
import org.dhis2.usescases.datasets.dataSetTable.DataSetTableModel
import org.dhis2.utils.analytics.AnalyticsHelper
import org.hisp.dhis.android.core.category.CategoryOption
import org.hisp.dhis.android.core.category.CategoryOptionCombo
import org.hisp.dhis.android.core.common.Access
import org.hisp.dhis.android.core.common.DataAccess
import org.hisp.dhis.android.core.common.ObjectWithUid
import org.hisp.dhis.android.core.common.ValueType
import org.hisp.dhis.android.core.dataelement.DataElement
import org.hisp.dhis.android.core.dataelement.DataElementOperand
import org.junit.Before
import org.junit.Test

class DataValuePresenterTest {

    private lateinit var presenter: DataValuePresenter

    private val view: DataValueContract.View = mock()
    private val dataValueRepository: DataValueRepository = mock()
    private val schedulers: SchedulerProvider = TrampolineSchedulerProvider()
    private val analyticsHelper: AnalyticsHelper = mock()
    private val valueStore: ValueStore = mock()
    private val prefsProvider: PreferenceProvider = mock()

    @Before
    fun setup() {
        presenter =
            DataValuePresenter(
                view,
                dataValueRepository,
                valueStore,
                schedulers,
                analyticsHelper,
                prefsProvider,
                "dataSetUid"
            )
    }

    @Test
    fun `Check all row have values`() {
        val dataValues: List<DataSetTableModel> = createDataValues()

        val tableCells: MutableList<List<List<FieldViewModel>>> = createTableCells()

        assertTrue(presenter.checkAllFieldRequired(tableCells, dataValues))
    }

    @Test
    fun `Check no row have values`() {
        val dataValues: List<DataSetTableModel> = listOf()

        val tableCells: MutableList<List<List<FieldViewModel>>> = createTableCells()

        assertTrue(presenter.checkAllFieldRequired(tableCells, dataValues))
    }

    @Test
    fun `Check one field without value`() {
        val dataValues: MutableList<DataSetTableModel> = createDataValues().toMutableList()

        dataValues.removeAt(0)

        val tableCells: MutableList<List<List<FieldViewModel>>> = createTableCells()

        assertFalse(presenter.checkAllFieldRequired(tableCells, dataValues))
        verify(view).highligthHeaderRow(0, 0, false)
    }

    @Test
    fun `Check all mandatory fields with value`() {
        val dataValues: List<DataSetTableModel> = createDataValues()

        val tableCells: MutableList<List<List<FieldViewModel>>> = createTableCells()

        assertTrue(presenter.checkMandatoryField(tableCells, dataValues))
    }

    @Test
    fun `Check mandatory field without value`() {
        val dataValues: MutableList<DataSetTableModel> = createDataValues().toMutableList()

        dataValues.removeAt(0)

        val tableCells: MutableList<List<List<FieldViewModel>>> = createTableCells()

        assertFalse(presenter.checkMandatoryField(tableCells, dataValues))
        verify(view).highligthHeaderRow(0, 0, true)
    }

    @Test
    fun `Validates if cell is disabled by greyed field configuration`() {
        val dataElement = DataElement.builder().uid("dataElement").build()
        val categoryOptionCombo = CategoryOptionCombo.builder().uid("categoryOptCombo").build()
        val dataElementsDisabled = listOf(
            DataElementOperand.builder()
                .uid("DEOuid")
                .dataElement(ObjectWithUid.create(dataElement.uid()))
                .categoryOptionCombo(ObjectWithUid.create(categoryOptionCombo.uid()))
                .build()
        )

        val categoryOption =
            CategoryOption.builder()
                .uid("uid")
                .access(Access.builder().data(DataAccess.builder().write(true).build()).build())
                .build()

        whenever(
            dataValueRepository.getCatOptionFromCatOptionCombo(categoryOptionCombo)
        ) doReturn mutableListOf(categoryOption)

        val isEditable =
            presenter.validateIfIsEditable(dataElementsDisabled, dataElement, categoryOptionCombo)

        assertTrue(!isEditable)
    }

    @Test
    fun `Validates if cell is disabled by categoryOption write permissions`() {
        val dataElement = DataElement.builder().uid("dataElement").build()
        val categoryOptionCombo = CategoryOptionCombo.builder().uid("categoryOptCombo").build()
        val dataElementsDisabled = emptyList<DataElementOperand>()

        val categoryOption =
            CategoryOption.builder()
                .uid("uid")
                .access(Access.builder().data(DataAccess.builder().write(false).build()).build())
                .build()

        whenever(
            dataValueRepository.getCatOptionFromCatOptionCombo(categoryOptionCombo)
        ) doReturn mutableListOf(categoryOption)

        val isEditable =
            presenter.validateIfIsEditable(dataElementsDisabled, dataElement, categoryOptionCombo)

        assertTrue(!isEditable)
    }

    @Test
    fun `Validates if cell is enable`() {
        val dataElement = DataElement.builder().uid("dataElement").build()
        val categoryOptionCombo = CategoryOptionCombo.builder().uid("categoryOptCombo").build()
        val dataElementsDisabled = listOf(
            DataElementOperand.builder()
                .uid("DEOuid")
                .dataElement(ObjectWithUid.create("dataElementDisabled"))
                .categoryOptionCombo(ObjectWithUid.create(categoryOptionCombo.uid()))
                .build()
        )

        val categoryOption =
            CategoryOption.builder()
                .uid("uid")
                .access(Access.builder().data(DataAccess.builder().write(true).build()).build())
                .build()

        whenever(
            dataValueRepository.getCatOptionFromCatOptionCombo(categoryOptionCombo)
        ) doReturn mutableListOf(categoryOption)

        val isEditable =
            presenter.validateIfIsEditable(dataElementsDisabled, dataElement, categoryOptionCombo)

        assertTrue(isEditable)
    }

    private fun createDataValues(): List<DataSetTableModel> {
        val dataValues = arrayListOf<DataSetTableModel>()
        repeat(2) { row ->
            repeat(2) { column ->
                dataValues.add(
                    DataSetTableModel.create(
                        0,
                        "$row",
                        "",
                        "",
                        "$column",
                        "",
                        "value",
                        "",
                        null,
                        null,
                        null
                    )
                )
            }
        }
        return dataValues
    }

    private fun createTableCells(): MutableList<List<List<FieldViewModel>>> {
        val table = arrayListOf<List<FieldViewModel>>()
        repeat(2) { row ->
            val fields = arrayListOf<FieldViewModel>()
            repeat(2) { column ->
                fields.add(
                    EditTextViewModel.create(
                        "",
                        "",
                        true,
                        "",
                        "",
                        1,
                        ValueType.TEXT,
                        "",
                        true,
                        "",
                        "$row",
                        listOf(),
                        "",
                        row,
                        column,
                        "$column",
                        ""
                    )

                )
            }
            table.add(fields)
        }
        return mutableListOf(table)
    }
}
