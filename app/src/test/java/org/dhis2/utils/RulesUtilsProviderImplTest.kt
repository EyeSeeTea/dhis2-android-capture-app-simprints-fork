package org.dhis2.utils

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.Flowable
import java.util.ArrayList
import org.dhis2.data.forms.dataentry.ValueStore
import org.dhis2.data.forms.dataentry.fields.FieldViewModelFactory
import org.dhis2.data.forms.dataentry.fields.FieldViewModelFactoryImpl
import org.dhis2.data.forms.dataentry.fields.optionset.OptionSetViewModel
import org.dhis2.form.model.FieldUiModel
import org.dhis2.form.model.StoreResult
import org.dhis2.form.model.ValueStoreResult
import org.dhis2.form.ui.provider.DisplayNameProvider
import org.dhis2.form.ui.provider.HintProvider
import org.dhis2.form.ui.provider.LayoutProvider
import org.dhis2.form.ui.style.FormUiColorFactory
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.common.ObjectStyle
import org.hisp.dhis.android.core.common.ObjectWithUid
import org.hisp.dhis.android.core.common.ValueType
import org.hisp.dhis.android.core.common.ValueTypeDeviceRendering
import org.hisp.dhis.android.core.option.OptionGroup
import org.hisp.dhis.rules.models.RuleActionAssign
import org.hisp.dhis.rules.models.RuleActionDisplayKeyValuePair
import org.hisp.dhis.rules.models.RuleActionDisplayText
import org.hisp.dhis.rules.models.RuleActionErrorOnCompletion
import org.hisp.dhis.rules.models.RuleActionHideField
import org.hisp.dhis.rules.models.RuleActionHideOption
import org.hisp.dhis.rules.models.RuleActionHideOptionGroup
import org.hisp.dhis.rules.models.RuleActionHideProgramStage
import org.hisp.dhis.rules.models.RuleActionHideSection
import org.hisp.dhis.rules.models.RuleActionSetMandatoryField
import org.hisp.dhis.rules.models.RuleActionShowError
import org.hisp.dhis.rules.models.RuleActionShowOptionGroup
import org.hisp.dhis.rules.models.RuleActionShowWarning
import org.hisp.dhis.rules.models.RuleActionWarningOnCompletion
import org.hisp.dhis.rules.models.RuleEffect
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class RulesUtilsProviderImplTest {

    private lateinit var ruleUtils: RulesUtilsProvider
    private lateinit var testFieldViewModels: MutableMap<String, FieldUiModel>
    private lateinit var fieldFactory: FieldViewModelFactory
    private val d2: D2 = Mockito.mock(D2::class.java, Mockito.RETURNS_DEEP_STUBS)
    private val valueStore: ValueStore = mock()
    private val colorFactory: FormUiColorFactory = mock()
    private val layoutProvider: LayoutProvider = mock()
    private val hintProvider: HintProvider = mock()
    private val displayNameProvider: DisplayNameProvider = mock()

    private val testRuleEffects = ArrayList<RuleEffect>()

    @Before
    fun setUp() {
        ruleUtils = RulesUtilsProviderImpl(d2)
        fieldFactory = FieldViewModelFactoryImpl(
            ValueType.values().map { it to it.name }.toMap(),
            false,
            colorFactory,
            layoutProvider,
            hintProvider,
            displayNameProvider
        )
        testFieldViewModels = getTestingFieldViewModels().associateBy { it.uid }.toMutableMap()
    }

    private fun getTestingFieldViewModels(): MutableList<FieldUiModel> {
        return arrayListOf(
            randomFieldViewModel("uid1", ValueType.TEXT, "section1", null),
            randomFieldViewModel("uid2", ValueType.TEXT, "section1"),
            randomFieldViewModel("uid3", ValueType.TEXT, "section2"),
            randomFieldViewModel("uid4", ValueType.TEXT, "section2"),
            randomFieldViewModel("uid5", ValueType.TEXT, "section2"),
            randomFieldViewModel("uid6", ValueType.TEXT, "section3"),
            randomFieldViewModel("uid7", ValueType.TEXT, "section3")
        )
    }

    private fun randomFieldViewModel(
        uid: String,
        valueType: ValueType,
        section: String,
        value: String? = "test"
    ): FieldUiModel {
        return fieldFactory.create(
            uid,
            "label",
            valueType,
            false,
            null,
            value,
            section,
            null,
            true,
            null,
            null,
            null,
            null,
            ObjectStyle.builder().build(),
            "",
            null,
            null,
            null,
            null
        )
    }

    @Test
    fun `Should update fieldViewModel with a warning message`() {
        val testingUid = "uid1"

        testRuleEffects.add(
            RuleEffect.create(
                "ruleUid",
                RuleActionShowWarning.create("content", "action_data", testingUid),
                "data"
            )
        )

        ruleUtils.applyRuleEffects(
            true,
            testFieldViewModels,
            Result.success(testRuleEffects),
            valueStore
        )

        Assert.assertNotNull(testFieldViewModels["uid1"]!!.warning)
        Assert.assertEquals(testFieldViewModels["uid1"]!!.warning, "content data")
    }

    @Test
    fun `Should update fieldViewModel with error message`() {
        val testingUid = "uid1"

        testRuleEffects.add(
            RuleEffect.create(
                "ruleUid",
                RuleActionShowError.create("content", "action_data", testingUid),
                "data"
            )
        )

        val testModel = testFieldViewModels[testingUid]

        val result = ruleUtils.applyRuleEffects(
            true,
            testFieldViewModels,
            Result.success(testRuleEffects),
            valueStore
        )

        Assert.assertNotNull(testFieldViewModels[testingUid]!!.error)
        Assert.assertEquals(testFieldViewModels[testingUid]!!.error, "content data")
        assertTrue(result.errorMap().size == 1)
        assertTrue(result.errorMap().containsKey(testingUid))
    }

    @Test
    fun `Should remove field from list`() {
        val testingUid = "uid3"
        testRuleEffects.add(
            RuleEffect.create(
                "ruleUid",
                RuleActionHideField.create("content", testingUid),
                "data"
            )
        )

        whenever(valueStore.saveWithTypeCheck(testingUid, null)) doReturn Flowable.just(
            StoreResult(
                testingUid,
                ValueStoreResult.VALUE_CHANGED
            )
        )

        val result = ruleUtils.applyRuleEffects(
            true,
            testFieldViewModels,
            Result.success(testRuleEffects),
            valueStore
        )

        Assert.assertFalse(testFieldViewModels.contains(testingUid))
        verify(valueStore, times(1)).saveWithTypeCheck(testingUid, null)
        assertTrue(result.fieldsToUpdate.contains(testingUid))
    }

    @Test
    fun `RuleActionDisplayText Should not add new DisplayViewModel`() {
        testRuleEffects.add(
            RuleEffect.create(
                "ruleUid",
                RuleActionDisplayText.createForFeedback("content", "action data"),
                "data"
            )
        )

        val testFieldViewModelSize = testFieldViewModels.size

        ruleUtils.applyRuleEffects(
            true,
            testFieldViewModels,
            Result.success(testRuleEffects),
            valueStore
        )

        assertTrue(testFieldViewModels.size == testFieldViewModelSize)
        assertTrue(!testFieldViewModels.containsKey("content"))
    }

    @Test
    fun `RuleActionDisplayKeyValuePair should not add new DisplayViewModel`() {
        testRuleEffects.add(
            RuleEffect.create(
                "ruleUid",
                RuleActionDisplayKeyValuePair.createForIndicators("content", "action data"),
                "data"
            )
        )

        val testFieldViewModelSize = testFieldViewModels.size

        ruleUtils.applyRuleEffects(
            true,
            testFieldViewModels,
            Result.success(testRuleEffects),
            valueStore
        )

        assertTrue(testFieldViewModels.size == testFieldViewModelSize)
        assertTrue(!testFieldViewModels.containsKey("content"))
    }

    @Test
    fun `RuleActionHideSection should remove all fieldViewModel from a given section`() {
        val testingSectionUid = "section2"
        testRuleEffects.add(
            RuleEffect.create(
                "ruleUid",
                RuleActionHideSection.create(testingSectionUid),
                "data"
            )
        )

        val mandatoryFieldUid = "uid3"
        testFieldViewModels.apply {
            put(mandatoryFieldUid, get(mandatoryFieldUid)!!.setFieldMandatory())
        }

        ruleUtils.applyRuleEffects(
            true,
            testFieldViewModels,
            Result.success(testRuleEffects),
            valueStore
        )

        assertTrue(testFieldViewModels[mandatoryFieldUid] != null)
        assertTrue(testFieldViewModels["uid4"] == null)
        assertTrue(testFieldViewModels["uid5"] == null)
    }

    @Test
    fun `RuleActionAssign should set a value to a given field without value`() {
        val testingUid = "uid1"

        testRuleEffects.add(
            RuleEffect.create(
                "ruleUid",
                RuleActionAssign.create("content", "data", testingUid),
                "data"
            )
        )

        val result = ruleUtils.applyRuleEffects(
            true,
            testFieldViewModels,
            Result.success(testRuleEffects),
            valueStore
        )

        verify(valueStore, times(1)).saveWithTypeCheck(testingUid, "data")
        assertTrue(testFieldViewModels[testingUid]!!.value.equals("data"))
        assertTrue(!testFieldViewModels[testingUid]!!.editable)
    }

    @Test
    fun `RuleActionAssign should set a value to a given field with value`() {
        val testingUid = "uid2"
        val testingUid2 = "uid3"

        testRuleEffects.add(
            RuleEffect.create(
                "ruleUid1",
                RuleActionAssign.create("content", "data", testingUid),
                "data"
            )
        )
        testRuleEffects.add(
            RuleEffect.create(
                "ruleUid2",
                RuleActionAssign.create("content", "data", testingUid2),
                "test"
            )
        )

        whenever(valueStore.saveWithTypeCheck(any(), any())) doReturn Flowable.just(
            StoreResult(
                testingUid,
                ValueStoreResult.VALUE_CHANGED
            )
        )

        val result = ruleUtils.applyRuleEffects(
            true,
            testFieldViewModels,
            Result.success(testRuleEffects),
            valueStore
        )

        verify(valueStore, times(1)).saveWithTypeCheck(testingUid, "data")
        verify(valueStore, times(0)).saveWithTypeCheck(testingUid2, "test")
        assertTrue(testFieldViewModels[testingUid]!!.value.equals("data"))
        assertTrue(testFieldViewModels[testingUid2]!!.value.equals("test"))
        assertTrue(!testFieldViewModels[testingUid]!!.editable)
        assertTrue(!testFieldViewModels[testingUid]!!.editable)
        assertTrue(result.fieldsToUpdate.size == 1)
        assertTrue(result.fieldsToUpdate.contains(testingUid))
    }

    @Test
    fun `RuleActionAssign should set a value to calculated value`() {
        testRuleEffects.add(
            RuleEffect.create(
                "ruleUid",
                RuleActionAssign.create("content", "data", null),
                "data"
            )
        )

        val result = ruleUtils.applyRuleEffects(
            true,
            testFieldViewModels,
            Result.success(testRuleEffects),
            valueStore
        )

        assertTrue(result.fieldsToUpdate.isEmpty())
    }

    @Test
    fun `RuleActionSetMandatory should mark field as mandatory`() {
        val testingUid = "uid2"

        testRuleEffects.add(
            RuleEffect.create(
                "ruleUid",
                RuleActionSetMandatoryField.create(testingUid),
                "data"
            )
        )

        ruleUtils.applyRuleEffects(
            true,
            testFieldViewModels,
            Result.success(testRuleEffects),
            valueStore
        )

        assertTrue(testFieldViewModels[testingUid]!!.mandatory)
    }

    @Test
    fun `RuleActionWarningOnCompletion should set warning to field and allow completion`() {
        val testingUid = "uid1"

        testRuleEffects.add(
            RuleEffect.create(
                "ruleUid",
                RuleActionWarningOnCompletion.create("content", "action_data", testingUid),
                "data"
            )
        )

        val result = ruleUtils.applyRuleEffects(
            true,
            testFieldViewModels,
            Result.success(testRuleEffects),
            valueStore
        )

        Assert.assertEquals(testFieldViewModels[testingUid]!!.warning, "content data")
        assertTrue(result.messageOnComplete == "content data")
        assertTrue(result.canComplete)
    }

    @Test
    fun `RuleActionErrorOnCompletion should set warning to field and not allow completion`() {
        val testingUid = "uid1"

        testRuleEffects.add(
            RuleEffect.create(
                "ruleUid",
                RuleActionErrorOnCompletion.create("content", "action_data", testingUid),
                "data"
            )
        )

        val result = ruleUtils.applyRuleEffects(
            true,
            testFieldViewModels,
            Result.success(testRuleEffects),
            valueStore
        )

        Assert.assertEquals(testFieldViewModels[testingUid]!!.error, "content data")
        assertTrue(result.messageOnComplete == "content data")
        assertTrue(!result.canComplete)
    }

    @Test
    fun `RuleActionHideProgramStage should execute callback action`() {
        val testingUid = "stageUid"
        testRuleEffects.add(
            RuleEffect.create(
                "ruleUid",
                RuleActionHideProgramStage.create(testingUid),
                "data"
            )
        )

        val result = ruleUtils.applyRuleEffects(
            true,
            testFieldViewModels,
            Result.success(testRuleEffects),
            valueStore
        )
    }

    @Test
    fun `RuleActionHideProgramStage should remove stage from possible selections`() {
    }

    @Test
    fun `RuleActionHideOption should execute callback action`() {
        testRuleEffects.add(
            RuleEffect.create(
                "ruleUid",
                RuleActionHideOption.create("content", "optionUid", "field"),
                "data"
            )
        )

        testFieldViewModels["field"] = OptionSetViewModel.create(
            "field",
            1,
            "label",
            false,
            "optionSetUid",
            null,
            null,
            true,
            null,
            ObjectStyle.builder().build(),
            false,
            "",
            ValueTypeDeviceRendering.builder().build(),
            emptyList(),
            ValueType.TEXT,
            null
        )

        whenever(valueStore.deleteOptionValueIfSelected(any(), any())) doReturn StoreResult(
            "field",
            ValueStoreResult.VALUE_HAS_NOT_CHANGED
        )

        val result = ruleUtils.applyRuleEffects(
            true,
            testFieldViewModels,
            Result.success(testRuleEffects),
            valueStore
        )

        assertTrue(
            (testFieldViewModels["field"] as OptionSetViewModel).optionsToHide.contains("optionUid")
        )

        verify(valueStore).deleteOptionValueIfSelected("field", "optionUid")
    }

    @Test
    fun `RuleActionHideOptionGroup should execute callback action`() {
        testRuleEffects.add(
            RuleEffect.create(
                "ruleUid",
                RuleActionHideOptionGroup.create("content", "optionGroupUid", "field"),
                "data"
            )
        )

        testFieldViewModels["field"] = OptionSetViewModel.create(
            "field",
            1,
            "label",
            false,
            "optionSetUid",
            null,
            null,
            true,
            null,
            ObjectStyle.builder().build(),
            false,
            "",
            ValueTypeDeviceRendering.builder().build(),
            emptyList(),
            ValueType.TEXT,
            null
        )

        whenever(
            valueStore.deleteOptionValueIfSelectedInGroup(
                any(),
                any(),
                any()
            )
        ) doReturn StoreResult(
            "field",
            ValueStoreResult.VALUE_HAS_NOT_CHANGED
        )

        mockD2OptionGroupCalls(
            "optionGroupUid",
            "optionToHide1", "optionToHide2"
        )

        ruleUtils.applyRuleEffects(
            true,
            testFieldViewModels,
            Result.success(testRuleEffects),
            valueStore
        )

        assertTrue(
            (testFieldViewModels["field"] as OptionSetViewModel)
                .optionsToHide.contains("optionToHide1")
        )
        assertTrue(
            (testFieldViewModels["field"] as OptionSetViewModel)
                .optionsToHide.contains("optionToHide2")
        )

        verify(
            valueStore
        ).deleteOptionValueIfSelectedInGroup("field", "optionGroupUid", true)
    }

    @Test
    fun `RuleActionShowOptionGroup should execute callback action`() {
        mockD2OptionGroupCalls(
            "optionGroupUid",
            "optionToShow1", "optionToShow2"
        )
        testRuleEffects.add(
            RuleEffect.create(
                "ruleUid",
                RuleActionShowOptionGroup.create("content", "optionGroupUid", "field"),
                "data"
            )
        )

        testFieldViewModels["field"] = OptionSetViewModel.create(
            "field",
            1,
            "label",
            false,
            "optionSetUid",
            null,
            null,
            true,
            null,
            ObjectStyle.builder().build(),
            false,
            "",
            ValueTypeDeviceRendering.builder().build(),
            emptyList(),
            ValueType.TEXT,
            null
        )

        ruleUtils.applyRuleEffects(
            true,
            testFieldViewModels,
            Result.success(testRuleEffects),
            valueStore
        ) /*{ optionGroupUids -> listOf("optionToShow1", "optionToShow2") }*/

        assertTrue(
            (testFieldViewModels["field"] as OptionSetViewModel)
                .optionsToShow.contains("optionToShow1")
        )
        assertTrue(
            (testFieldViewModels["field"] as OptionSetViewModel)
                .optionsToShow.contains("optionToShow2")
        )
        verify(valueStore).deleteOptionValueIfSelectedInGroup("field", "optionGroupUid", false)
    }

    @Test
    fun `Should not assign value to a hidden field`() {
        val testingUid = "uid3"
        testRuleEffects.add(
            RuleEffect.create(
                "ruleUid",
                RuleActionHideField.create("content", testingUid),
                "data"
            )
        )
        testRuleEffects.add(
            RuleEffect.create(
                "ruleUid2",
                RuleActionAssign.create("content", "data", testingUid),
                "data"
            )
        )

        whenever(valueStore.saveWithTypeCheck(testingUid, null)) doReturn Flowable.just(
            StoreResult(
                testingUid,
                ValueStoreResult.VALUE_CHANGED
            )
        )

        val result = ruleUtils.applyRuleEffects(
            true,
            testFieldViewModels,
            Result.success(testRuleEffects),
            valueStore
        )

        Assert.assertFalse(testFieldViewModels.contains(testingUid))
        verify(valueStore, times(1)).saveWithTypeCheck(testingUid, null)
        verify(valueStore, times(0)).saveWithTypeCheck(testingUid, "data")
        assertTrue(result.fieldsToUpdate.contains(testingUid))
    }

    private fun mockD2OptionGroupCalls(optionGroupUid: String, vararg optionUidsToReturn: String) {
        whenever(
            d2.optionModule().optionGroups()
        ) doReturn mock()
        whenever(
            d2.optionModule().optionGroups()
                .withOptions()
        ) doReturn mock()
        whenever(
            d2.optionModule().optionGroups()
                .withOptions()
                .byUid()
        ) doReturn mock()
        whenever(
            d2.optionModule().optionGroups()
                .withOptions()
                .byUid().`in`(listOf(optionGroupUid))
        ) doReturn mock()
        whenever(
            d2.optionModule().optionGroups()
                .withOptions()
                .byUid().`in`(listOf(optionGroupUid))
                .blockingGet()
        ) doReturn listOf(
            OptionGroup.builder()
                .uid(optionGroupUid)
                .options(
                    optionUidsToReturn.map { ObjectWithUid.create(it) }.toList()
                )
                .build()
        )
    }
}
