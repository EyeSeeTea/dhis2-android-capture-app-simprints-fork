package org.dhis2.usescases.teiDashboard.dashboardsfragments.feedback

import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.Single
import org.dhis2.core.types.TreeNode
import org.dhis2.usescases.teiDashboard.dashboardfragments.teidata.TeiDataRepository
import org.dhis2.usescases.teiDashboard.dashboardfragments.teidata.teievents.EventViewModel
import org.dhis2.usescases.teiDashboard.dashboardfragments.teidata.teievents.EventViewModelType
import org.hisp.dhis.android.core.event.Event
import org.hisp.dhis.android.core.program.ProgramStage
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class GetFeedbackTest {

    @Mock
    lateinit var teiDataRepository: TeiDataRepository

    @Mock
    lateinit var valuesRepository: ValuesRepository

    @Test
    fun `should return not found failure if there are not events`() {
        givenThatThereNotEvents()

        val getFeedback = GetFeedback(teiDataRepository, valuesRepository)

        val feedbackResult =
            getFeedback(FeedbackMode.ByEvent, null, false)

        feedbackResult.fold(
            { failure -> Assert.assertTrue(failure is FeedbackFailure.NotFound) },
            { success -> Assert.fail("$success should be FeedbackFailure.NotFound") })
    }

    @Test
    fun `should not return feedback if there are events without values`() {
        givenAnEventsWithoutValues()

        val getFeedback = GetFeedback(teiDataRepository, valuesRepository)
        val feedbackResult =
            getFeedback(FeedbackMode.ByEvent, null, false)

        feedbackResult.fold(
            { failure -> Assert.fail("$failure should be success") },
            { feedback -> Assert.assertEquals(listOf<List<TreeNode<FeedbackItem>>>(), feedback) })
    }

    @Test
    fun `should return expected feedback by events`() {
        givenOneEventWithValues(
            "ART New", listOf(
                listOf("1", "Completeness", "Partly", "#FFC700", "Feedback Completeness"),
                listOf("2", "Timeliness", "100%", "#0CE922", "Feedback Timeliness"),
                listOf("1.1", "Completeness 1.1", "86%", "#FFC700", "Feedback Completeness 1.1"),
                listOf("1.2", "Completeness 1.2", "56%", "#c80f26", "Feedback Completeness 1.2")
            )
        )

        val getFeedback = GetFeedback(teiDataRepository, valuesRepository)
        val feedbackResult = getFeedback(FeedbackMode.ByEvent)

        val expectedFeedback = listOf(
            TreeNode.Node(
                FeedbackItem("ART New", null, "ART New UID"),
                listOf(
                    TreeNode.Node(
                        FeedbackItem(
                            "Completeness", FeedbackItemValue("Partly", "#FFC700"),
                            "Completeness_DE"
                        ),
                        listOf(
                            TreeNode.Leaf(FeedbackHelpItem("Feedback Completeness")),
                            TreeNode.Node(
                                FeedbackItem(
                                    "Completeness 1.1", FeedbackItemValue("86%", "#FFC700"),
                                    "Completeness 1.1_DE"
                                ),
                                listOf(
                                    TreeNode.Leaf(FeedbackHelpItem("Feedback Completeness 1.1"))
                                )
                            ),
                            TreeNode.Node(
                                FeedbackItem(
                                    "Completeness 1.2", FeedbackItemValue("56%", "#c80f26"),
                                    "Completeness 1.2_DE"
                                ),
                                listOf(
                                    TreeNode.Leaf(FeedbackHelpItem("Feedback Completeness 1.2"))
                                )
                            )
                        )
                    ),
                    TreeNode.Node(
                        FeedbackItem(
                            "Timeliness", FeedbackItemValue("100%", "#0CE922"),
                            "Timeliness_DE"
                        ),
                        listOf(
                            TreeNode.Leaf(FeedbackHelpItem("Feedback Timeliness"))
                        )
                    )
                )
            )
        )

        feedbackResult.fold(
            { failure -> Assert.fail("$failure should be success") },
            { feedback -> Assert.assertEquals(expectedFeedback.toList(), feedback.toList()) })
    }

    private fun givenThatThereNotEvents() {
        whenever(
            teiDataRepository.getTEIEnrollmentEvents(
                null, false, mutableListOf(), mutableListOf(),
                mutableListOf(), false, mutableListOf(), mutableListOf()
            )
        ).thenReturn(Single.just(listOf()))
    }

    private fun givenAnEventsWithoutValues() {
        val events = listOf(
            EventViewModel(
                EventViewModelType.EVENT,
                ProgramStage.builder().displayName("EVENT1").uid("STAGE_UID").build(),
                Event.builder().uid("EVENT1_UID").build(), 0, null, true, true
            ),
            EventViewModel(
                EventViewModelType.EVENT,
                ProgramStage.builder().displayName("EVENT2").uid("STAGE_UID").build(),
                Event.builder().uid("EVENT2_UID").build(), 0, null, true, true
            )
        )

        whenever(
            teiDataRepository.getTEIEnrollmentEvents(
                null, false, mutableListOf(), mutableListOf(),
                mutableListOf(), false, mutableListOf(), mutableListOf()
            )
        ).thenReturn(Single.just(events))

        events.forEach {
            whenever(valuesRepository.getByEvent(it.event!!.uid())).thenReturn(listOf())
        }
    }

    private fun givenOneEventWithValues(stageName: String, valuesData: List<List<String>>) {
        whenever(
            teiDataRepository.getTEIEnrollmentEvents(
                null, false, mutableListOf(), mutableListOf(),
                mutableListOf(), false, mutableListOf(), mutableListOf()
            )
        ).thenReturn(
            Single.just(
                listOf(
                    EventViewModel(
                        EventViewModelType.EVENT,
                        ProgramStage.builder().displayName(stageName).uid("STAGE_UID").build(),
                        Event.builder().uid("$stageName UID").build(), 0, null, true, true
                    )
                )
            )
        )

        val values = valuesData.map {
            Value(
                "${it[1]}_DE", it[1], it[2], FeedbackOrder(it[0]), it[3], it[4],
                "$stageName UID"
            )
        }

        whenever(valuesRepository.getByEvent("$stageName UID")).thenReturn(values)
    }

    companion object {
    }
}