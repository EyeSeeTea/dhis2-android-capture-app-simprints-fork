package dhis2.org.analytics.charts.mappers

import dhis2.org.analytics.charts.data.ChartType
import dhis2.org.analytics.charts.data.DimensionalVisualization
import dhis2.org.analytics.charts.data.Graph
import dhis2.org.analytics.charts.data.SerieData
import dhis2.org.analytics.charts.data.toAnalyticsChartType
import dhis2.org.analytics.charts.providers.ChartCoordinatesProvider
import dhis2.org.analytics.charts.providers.PeriodStepProvider
import java.util.Locale
import org.hisp.dhis.android.core.analytics.aggregated.Dimension
import org.hisp.dhis.android.core.analytics.aggregated.GridAnalyticsResponse
import org.hisp.dhis.android.core.analytics.aggregated.MetadataItem
import org.hisp.dhis.android.core.common.RelativePeriod
import org.hisp.dhis.android.core.period.PeriodType
import org.hisp.dhis.android.core.visualization.Visualization
import org.hisp.dhis.android.core.visualization.VisualizationType

class VisualizationToGraph(
    private val periodStepProvider: PeriodStepProvider,
    private val chartCoordinatesProvider: ChartCoordinatesProvider
) {
    val dimensionalResponseToPieData by lazy { DimensionalResponseToPieData() }

    fun map(visualizations: List<DimensionalVisualization>): List<Graph> {
        return visualizations.map { visualization: DimensionalVisualization ->
            val series = when (visualization.chartType) {
                ChartType.PIE_CHART -> dimensionalResponseToPieData.map(
                    visualization.dimensionResponse,
                    Dimension.Data
                )
                else -> emptyList()
            }
            val categories = emptyList<String>()
            Graph(
                title = visualization.name,
                series = series,
                periodToDisplayDefault = null,
                eventPeriodType = PeriodType.Daily,
                periodStep = periodStepProvider.periodStep(PeriodType.Daily),
                chartType = visualization.chartType,
                categories = categories,
                visualizationUid = null
            )
        }
    }

    fun mapToGraph(
        visualization: Visualization,
        gridAnalyticsResponse: GridAnalyticsResponse,
        selectedRelativePeriod: RelativePeriod?,
        selectedOrgUnits: List<String>?
    ): Graph {
        val period = visualization.relativePeriods()?.filter { it.value }?.keys?.first()
        val categories = getCategories(visualization.type(), gridAnalyticsResponse)
        val formattedCategory = formatCategories(period, categories, gridAnalyticsResponse.metadata)

        return Graph(
            title = visualization.displayName() ?: "",
            series = getSeries(gridAnalyticsResponse, categories),
            periodToDisplayDefault = null,
            eventPeriodType = PeriodType.Monthly,
            periodStep = periodStepProvider.periodStep(PeriodType.Monthly),
            chartType = visualization.type().toAnalyticsChartType(),
            categories = formattedCategory,
            visualizationUid = visualization.uid(),
            periodToDisplaySelected = selectedRelativePeriod,
            orgUnitsSelected = selectedOrgUnits ?: emptyList()
        )
    }

    fun addErrorGraph(
        visualization: Visualization,
        selectedRelativePeriod: RelativePeriod?,
        selectedOrgUnits: List<String>?
    ): Graph {
        return Graph(
            title = visualization.displayName() ?: "",
            series = emptyList(),
            periodToDisplayDefault = null,
            eventPeriodType = PeriodType.Monthly,
            periodStep = periodStepProvider.periodStep(PeriodType.Monthly),
            chartType = visualization.type().toAnalyticsChartType(),
            categories = emptyList(),
            visualizationUid = visualization.uid(),
            periodToDisplaySelected = selectedRelativePeriod,
            orgUnitsSelected = selectedOrgUnits ?: emptyList(),
            hasError = true
        )
    }

    private fun getCategories(
        visualizationType: VisualizationType?,
        gridAnalyticsResponse: GridAnalyticsResponse
    ): List<String> {
        return when (visualizationType) {
            VisualizationType.PIE -> {
                listOf("Values")
            }
            else -> {
                gridAnalyticsResponse.headers.rows.firstOrNull()?.map {
                    gridAnalyticsResponse.metadata[it.id]!!.displayName
                } ?: emptyList()
            }
        }
    }

    private fun formatCategories(
        period: RelativePeriod?,
        categories: List<String>,
        metadata: Map<String, MetadataItem>
    ): List<String> {
        return period?.let {
            categories.map { category ->
                when (val metadataItem = metadata[category]) {
                    is MetadataItem.PeriodItem -> periodStepProvider.periodUIString(
                        Locale.getDefault(),
                        metadataItem.item
                    )
                    else -> category
                }
            }
        } ?: categories
    }

    private fun getSeries(
        gridAnalyticsResponse: GridAnalyticsResponse,
        categories: List<String>
    ): List<SerieData> {
        val serieList = (gridAnalyticsResponse.values.first().indices).map { idx ->
            gridAnalyticsResponse.values.map { it[idx] }
        }

        return serieList.map { gridResponseValueList ->
            val fieldId = gridResponseValueList.first().columns.first()
            val fieldName = gridAnalyticsResponse.metadata[fieldId]!!.displayName
            SerieData(
                fieldName = fieldName,
                coordinates = chartCoordinatesProvider.visualizationCoordinates(
                    gridResponseValueList,
                    gridAnalyticsResponse.metadata,
                    categories
                )
            )
        }
    }
}
