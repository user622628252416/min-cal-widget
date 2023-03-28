// Copyright (c) 2016, Miquel Martí <miquelmarti111@gmail.com>
// See LICENSE for licensing information
package cat.mvmike.minimalcalendarwidget.domain.component

import android.widget.RemoteViews
import cat.mvmike.minimalcalendarwidget.BaseTest
import cat.mvmike.minimalcalendarwidget.domain.Day
import cat.mvmike.minimalcalendarwidget.domain.Instance
import cat.mvmike.minimalcalendarwidget.domain.component.DaysService.getNumberOfInstances
import cat.mvmike.minimalcalendarwidget.domain.configuration.item.Colour
import cat.mvmike.minimalcalendarwidget.domain.configuration.item.Format
import cat.mvmike.minimalcalendarwidget.domain.configuration.item.SymbolSet
import cat.mvmike.minimalcalendarwidget.domain.configuration.item.Theme
import cat.mvmike.minimalcalendarwidget.domain.configuration.item.Transparency
import cat.mvmike.minimalcalendarwidget.domain.intent.ActionableView
import cat.mvmike.minimalcalendarwidget.infrastructure.resolver.CalendarResolver
import cat.mvmike.minimalcalendarwidget.infrastructure.resolver.GraphicResolver
import cat.mvmike.minimalcalendarwidget.infrastructure.resolver.SystemResolver
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDate.of
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Random
import java.util.stream.Stream

private const val DAY_CELL_TRANSPARENT_BACKGROUND = "transparentBackground"
private const val DAY_CELL_MODERATE_TRANSPARENT_BACKGROUND_IN_HEX = "#40ground"
private const val DAY_CELL_LOW_TRANSPARENT_BACKGROUND_IN_HEX = "#18ground"

internal class DaysServiceTest : BaseTest() {

    private val widgetRv = mockk<RemoteViews>()

    private val rowRv = mockk<RemoteViews>()

    private val dayRv = mockk<RemoteViews>()

    @ParameterizedTest
    @MethodSource("getFormatAndFocusOnCurrentWeekWithExpectedOutput")
    fun draw_shouldReturnSafeDateSpanOfSystemTimeZoneInstances(testProperties: DrawDaysUseCaseTestProperties) {
        mockkObject(ActionableView.CellDay)
        mockGetSystemLocalDate()
        mockIsReadCalendarPermitted(true)

        val initLocalDate = systemLocalDate.minusDays(45)
        val endLocalDate = systemLocalDate.plusDays(45)
        val initEpochMillis = initLocalDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endEpochMillis = endLocalDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        mockGetSystemZoneId()
        every { CalendarResolver.getInstances(context, initEpochMillis, endEpochMillis) } returns getSystemInstances()

        val symbolSet = SymbolSet.MINIMAL
        val theme = Theme.DARK
        val instancesColour = Colour.CYAN
        mockSharedPreferences()
        mockShowDeclinedEvents()
        mockWidgetTransparency(Transparency(20))
        mockFirstDayOfWeek(DayOfWeek.MONDAY)
        mockFocusOnCurrentWeek(testProperties.focusOnCurrentWeek)
        mockWidgetTheme(theme)
        mockInstancesSymbolSet(symbolSet)
        mockInstancesColour(instancesColour)

        every { GraphicResolver.createDaysRow(context) } returns rowRv

        listOf(
            instancesColour.getInstancesColour(true, theme),
            instancesColour.getInstancesColour(false, theme)
        ).forEach {
            every { GraphicResolver.getColour(context, it) } returns it
        }
        val expectedBackground = Random().nextInt()
        listOf(
            theme.getCellDay(isToday = true, inMonth = true, dayOfWeek = DayOfWeek.TUESDAY),
            theme.getCellDay(isToday = false, inMonth = true, dayOfWeek = DayOfWeek.TUESDAY),
            theme.getCellDay(isToday = false, inMonth = true, dayOfWeek = DayOfWeek.SATURDAY),
            theme.getCellDay(isToday = false, inMonth = true, dayOfWeek = DayOfWeek.SUNDAY),
            theme.getCellDay(isToday = false, inMonth = false, dayOfWeek = DayOfWeek.SATURDAY),
            theme.getCellDay(isToday = false, inMonth = false, dayOfWeek = DayOfWeek.SUNDAY)
        ).forEach {
            every {
                GraphicResolver.getColourAsString(context, it.background!!)
            } returns DAY_CELL_TRANSPARENT_BACKGROUND
        }
        every {
            GraphicResolver.parseColour(DAY_CELL_MODERATE_TRANSPARENT_BACKGROUND_IN_HEX)
        } returns expectedBackground
        every {
            GraphicResolver.parseColour(DAY_CELL_LOW_TRANSPARENT_BACKGROUND_IN_HEX)
        } returns expectedBackground

        every { GraphicResolver.createDay(context, any()) } returns dayRv
        justRun {
            GraphicResolver.addToDaysRow(context, rowRv, any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
        justRun { ActionableView.CellDay.addListener(context, dayRv, any()) }
        justRun { GraphicResolver.addToWidget(widgetRv, rowRv) }

        DaysService.draw(context, widgetRv, testProperties.format)

        verify { SystemResolver.getSystemLocalDate() }
        verify { CalendarResolver.isReadCalendarPermitted(context) }
        verify { SystemResolver.getSystemZoneId() }
        verify { CalendarResolver.getInstances(context, initEpochMillis, endEpochMillis) }

        verifyShowDeclinedEvents()
        verifyWidgetTransparency()
        verifyFirstDayOfWeek()
        verifyFocusOnCurrentWeek()
        verifyWidgetTheme()
        verifyInstancesSymbolSet()
        verifyInstancesColour()

        verify(exactly = 6) { GraphicResolver.createDaysRow(context) }

        testProperties.expectedDayProperties.forEach { dayUseCaseTest ->

            val instancesColourId = instancesColour.getInstancesColour(dayUseCaseTest.isToday, theme)
            verify { GraphicResolver.getColour(context, instancesColourId) }

            val cellDay = theme.getCellDay(dayUseCaseTest.isToday, dayUseCaseTest.isInMonth, dayUseCaseTest.dayOfWeek)
            cellDay.background?.let {
                verify {
                    GraphicResolver.getColourAsString(context, it)
                    when (dayUseCaseTest.dayOfWeek) {
                        DayOfWeek.SATURDAY,
                        DayOfWeek.SUNDAY -> GraphicResolver.parseColour(DAY_CELL_MODERATE_TRANSPARENT_BACKGROUND_IN_HEX)
                        else -> GraphicResolver.parseColour(DAY_CELL_LOW_TRANSPARENT_BACKGROUND_IN_HEX)
                    }
                }
            }

            verify {
                GraphicResolver.createDay(context, cellDay.layout)
                GraphicResolver.addToDaysRow(
                    context = context,
                    weekRowRemoteView = rowRv,
                    dayRemoteView = dayRv,
                    viewId = cellDay.id,
                    text = dayUseCaseTest.text,
                    textColour = cellDay.textColour,
                    dayOfMonthInBold = dayUseCaseTest.isToday,
                    instancesColour = instancesColourId,
                    instancesRelativeSize = symbolSet.relativeSize,
                    dayBackgroundColour = cellDay.background?.let { expectedBackground },
                    textRelativeSize = testProperties.format.dayCellTextRelativeSize
                )
                ActionableView.CellDay.addListener(context, dayRv, dayUseCaseTest.startOfDay(systemZoneOffset))
            }
        }
        verify(exactly = 6) { GraphicResolver.addToWidget(widgetRv, rowRv) }
        confirmVerified(widgetRv, rowRv)
    }

    @ParameterizedTest
    @MethodSource("getSystemLocalDateAndFirstDayOfWeekWithExpectedCurrentWeekFocusedInitialLocalDate")
    fun getFocusedOnCurrentWeekInitialLocalDate_shouldReturnWidgetInitialDate(
        systemLocalDate: LocalDate,
        firstDayOfWeek: DayOfWeek,
        expectedInitialLocalDate: LocalDate
    ) {
        val result = DaysService.getFocusedOnCurrentWeekInitialLocalDate(systemLocalDate, firstDayOfWeek)

        assertThat(result).isEqualTo(expectedInitialLocalDate)
    }

    @ParameterizedTest
    @MethodSource("getSystemLocalDateAndFirstDayOfWeekWithExpectedNaturalMonthInitialLocalDate")
    fun getNaturalMonthInitialLocalDate_shouldReturnWidgetInitialDate(
        systemLocalDate: LocalDate,
        firstDayOfWeek: DayOfWeek,
        expectedInitialLocalDate: LocalDate
    ) {
        val result = DaysService.getNaturalMonthInitialLocalDate(systemLocalDate, firstDayOfWeek)

        assertThat(result).isEqualTo(expectedInitialLocalDate)
    }

    @ParameterizedTest
    @MethodSource("getLocalDateAndIncludeDeclinedEventsWithExpectedNumberOfInstances")
    fun getNumberOfInstances_shouldReturnEventsInDayAndConsideringDeclinedEvents(
        localDate: LocalDate,
        includeDeclinedEvents: Boolean,
        expectedNumberOfInstances: Int
    ) {
        val result = Day(localDate).getNumberOfInstances(getSystemInstances(), includeDeclinedEvents)

        assertThat(result).isEqualTo(expectedNumberOfInstances)
    }

    private fun getSystemInstances(): Set<Instance> {
        val random = Random()
        return setOf(
            Instance(
                eventId = random.nextInt(),
                start = "2018-11-26T00:00:00Z".toInstant(ZoneOffset.UTC),
                end = "2018-11-27T00:00:00Z".toInstant(ZoneOffset.UTC),
                zoneId = ZoneOffset.UTC,
                isDeclined = false
            ),
            Instance(
                eventId = random.nextInt(),
                start = "2018-11-28T00:00:00Z".toInstant(systemZoneOffset),
                end = "2018-11-29T09:00:00Z".toInstant(systemZoneOffset),
                zoneId = systemZoneOffset,
                isDeclined = false
            ),
            Instance(
                eventId = random.nextInt(),
                start = "2018-12-03T00:00:00Z".toInstant(ZoneOffset.UTC),
                end = "2018-12-04T00:00:00Z".toInstant(ZoneOffset.UTC),
                zoneId = ZoneOffset.UTC,
                isDeclined = false
            ),
            Instance(
                eventId = random.nextInt(),
                start = "2018-12-04T00:00:00Z".toInstant(ZoneOffset.UTC),
                end = "2018-12-05T00:00:00Z".toInstant(ZoneOffset.UTC),
                zoneId = ZoneOffset.UTC,
                isDeclined = false
            ),
            Instance(
                eventId = random.nextInt(),
                start = "2018-12-06T00:00:00Z".toInstant(ZoneOffset.UTC),
                end = "2018-12-07T00:00:00Z".toInstant(ZoneOffset.UTC),
                zoneId = ZoneOffset.UTC,
                isDeclined = false
            ),
            Instance(
                eventId = random.nextInt(),
                start = "2018-12-06T02:00:00Z".toInstant(systemZoneOffset),
                end = "2018-12-07T04:00:00Z".toInstant(systemZoneOffset),
                zoneId = systemZoneOffset,
                isDeclined = false
            ),
            Instance(
                eventId = random.nextInt(),
                start = "2018-12-06T00:00:00Z".toInstant(ZoneOffset.UTC),
                end = "2018-12-07T00:00:00Z".toInstant(ZoneOffset.UTC),
                zoneId = ZoneOffset.UTC,
                isDeclined = false
            ),
            Instance(
                eventId = random.nextInt(),
                start = "2018-12-10T00:00:00Z".toInstant(ZoneOffset.UTC),
                end = "2018-12-11T00:00:00Z".toInstant(ZoneOffset.UTC),
                zoneId = ZoneOffset.UTC,
                isDeclined = false
            ),
            Instance(
                eventId = random.nextInt(),
                start = "2018-12-10T00:00:00Z".toInstant(ZoneOffset.UTC),
                end = "2018-12-11T00:00:00Z".toInstant(ZoneOffset.UTC),
                zoneId = ZoneOffset.UTC,
                isDeclined = false
            ),
            Instance(
                eventId = random.nextInt(),
                start = "2018-12-10T00:00:00Z".toInstant(ZoneOffset.UTC),
                end = "2018-12-11T00:00:00Z".toInstant(ZoneOffset.UTC),
                zoneId = ZoneOffset.UTC,
                isDeclined = false
            ),
            Instance(
                eventId = random.nextInt(),
                start = "2018-12-10T12:00:00Z".toInstant(systemZoneOffset),
                end = "2018-12-11T13:00:00Z".toInstant(systemZoneOffset),
                zoneId = systemZoneOffset,
                isDeclined = false
            ),
            Instance(
                eventId = random.nextInt(),
                start = "2018-12-18T00:00:00Z".toInstant(ZoneOffset.UTC),
                end = "2018-12-19T00:00:00Z".toInstant(ZoneOffset.UTC),
                zoneId = ZoneOffset.UTC,
                isDeclined = true
            ),
            Instance(
                eventId = random.nextInt(),
                start = "2018-12-27T00:00:00Z".toInstant(ZoneOffset.UTC),
                end = "2018-12-28T00:00:00Z".toInstant(ZoneOffset.UTC),
                zoneId = ZoneOffset.UTC,
                isDeclined = false
            ),
            Instance(
                eventId = random.nextInt(),
                start = "2018-12-30T00:00:00Z".toInstant(ZoneOffset.UTC),
                end = "2018-12-31T00:00:00Z".toInstant(ZoneOffset.UTC),
                zoneId = ZoneOffset.UTC,
                isDeclined = false
            ),
            Instance(
                eventId = random.nextInt(),
                start = "2018-12-30T00:00:00Z".toInstant(ZoneOffset.UTC),
                end = "2018-12-31T00:00:00Z".toInstant(ZoneOffset.UTC),
                zoneId = ZoneOffset.UTC,
                isDeclined = false
            ),
            Instance(
                eventId = random.nextInt(),
                start = "2018-12-30T00:00:00Z".toInstant(ZoneOffset.UTC),
                end = "2018-12-31T00:00:00Z".toInstant(ZoneOffset.UTC),
                zoneId = ZoneOffset.UTC,
                isDeclined = false
            ),
            Instance(
                eventId = random.nextInt(),
                start = "2018-12-30T00:00:00Z".toInstant(ZoneOffset.UTC),
                end = "2018-12-31T00:00:00Z".toInstant(ZoneOffset.UTC),
                zoneId = ZoneOffset.UTC,
                isDeclined = false
            ),
            Instance(
                eventId = random.nextInt(),
                start = "2018-12-30T00:00:00Z".toInstant(ZoneOffset.UTC),
                end = "2018-12-31T00:00:00Z".toInstant(ZoneOffset.UTC),
                zoneId = ZoneOffset.UTC,
                isDeclined = false
            ),
            Instance(
                eventId = random.nextInt(),
                start = "2019-01-01T05:00:00Z".toInstant(systemZoneOffset),
                end = "2019-12-02T11:20:00Z".toInstant(systemZoneOffset),
                zoneId = systemZoneOffset,
                isDeclined = false
            ),
            Instance(
                eventId = random.nextInt(),
                start = "2019-01-02T05:00:00Z".toInstant(systemZoneOffset),
                end = "2019-10-02T11:20:00Z".toInstant(systemZoneOffset),
                zoneId = systemZoneOffset,
                isDeclined = true
            ),
            Instance(
                eventId = random.nextInt(),
                start = "2019-01-05T00:00:00Z".toInstant(ZoneOffset.UTC),
                end = "2019-01-06T00:00:00Z".toInstant(ZoneOffset.UTC),
                zoneId = ZoneOffset.UTC,
                isDeclined = false
            ),
            Instance(
                eventId = random.nextInt(),
                start = "2019-01-05T00:00:00Z".toInstant(ZoneOffset.UTC),
                end = "2019-01-06T00:00:00Z".toInstant(ZoneOffset.UTC),
                zoneId = ZoneOffset.UTC,
                isDeclined = false
            ),
            Instance(
                eventId = random.nextInt(),
                start = "2019-01-05T00:00:00Z".toInstant(ZoneOffset.UTC),
                end = "2019-01-06T00:00:00Z".toInstant(ZoneOffset.UTC),
                zoneId = ZoneOffset.UTC,
                isDeclined = false
            ),
            Instance(
                eventId = random.nextInt(),
                start = "2019-01-05T00:00:00Z".toInstant(ZoneOffset.UTC),
                end = "2019-01-06T00:00:00Z".toInstant(ZoneOffset.UTC),
                zoneId = ZoneOffset.UTC,
                isDeclined = false
            ),
            Instance(
                eventId = random.nextInt(),
                start = "2019-01-05T00:00:00Z".toInstant(ZoneOffset.UTC),
                end = "2019-01-06T00:00:00Z".toInstant(ZoneOffset.UTC),
                zoneId = ZoneOffset.UTC,
                isDeclined = false
            ),
            Instance(
                eventId = random.nextInt(),
                start = "2019-01-05T00:00:00Z".toInstant(ZoneOffset.UTC),
                end = "2019-01-06T00:00:00Z".toInstant(ZoneOffset.UTC),
                zoneId = ZoneOffset.UTC,
                isDeclined = false
            )
        )
    }

    private fun getFormatAndFocusOnCurrentWeekWithExpectedOutput() = Stream.of(
        DrawDaysUseCaseTestProperties(Format(50), false, getDrawDaysUseCaseTestProperties()),
        DrawDaysUseCaseTestProperties(Format(130), true, getDrawDaysUseCaseTestProperties()),
        DrawDaysUseCaseTestProperties(Format(185), false, getDrawDaysUseCaseTestProperties()),
        DrawDaysUseCaseTestProperties(Format(200), true, getDrawDaysUseCaseTestProperties()),
        DrawDaysUseCaseTestProperties(Format(250), false, getDrawDaysUseCaseTestProperties()),
        DrawDaysUseCaseTestProperties(Format(1000), true, getDrawDaysUseCaseTestProperties())
    )!!

    private fun getDrawDaysUseCaseTestProperties() = Stream.of(
        DrawDaysUseCaseTestDayProperties("2018-11-26", " 26 ·", DayOfWeek.MONDAY),
        DrawDaysUseCaseTestDayProperties("2018-11-27", " 27  ", DayOfWeek.TUESDAY),
        DrawDaysUseCaseTestDayProperties("2018-11-28", " 28 ·", DayOfWeek.WEDNESDAY),
        DrawDaysUseCaseTestDayProperties("2018-11-29", " 29 ·", DayOfWeek.THURSDAY),
        DrawDaysUseCaseTestDayProperties("2018-11-30", " 30  ", DayOfWeek.FRIDAY),
        DrawDaysUseCaseTestDayProperties("2018-12-01", "  1  ", DayOfWeek.SATURDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-02", "  2  ", DayOfWeek.SUNDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-03", "  3 ·", DayOfWeek.MONDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-04", "  4 ·", DayOfWeek.TUESDAY, true, isToday = true),
        DrawDaysUseCaseTestDayProperties("2018-12-05", "  5  ", DayOfWeek.WEDNESDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-06", "  6 ∴", DayOfWeek.THURSDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-07", "  7 ·", DayOfWeek.FRIDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-08", "  8  ", DayOfWeek.SATURDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-09", "  9  ", DayOfWeek.SUNDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-10", " 10 ∷", DayOfWeek.MONDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-11", " 11 ·", DayOfWeek.TUESDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-12", " 12  ", DayOfWeek.WEDNESDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-13", " 13  ", DayOfWeek.THURSDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-14", " 14  ", DayOfWeek.FRIDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-15", " 15  ", DayOfWeek.SATURDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-16", " 16  ", DayOfWeek.SUNDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-17", " 17  ", DayOfWeek.MONDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-18", " 18  ", DayOfWeek.TUESDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-19", " 19  ", DayOfWeek.WEDNESDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-20", " 20  ", DayOfWeek.THURSDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-21", " 21  ", DayOfWeek.FRIDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-22", " 22  ", DayOfWeek.SATURDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-23", " 23  ", DayOfWeek.SUNDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-24", " 24  ", DayOfWeek.MONDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-25", " 25  ", DayOfWeek.TUESDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-26", " 26  ", DayOfWeek.WEDNESDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-27", " 27 ·", DayOfWeek.THURSDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-28", " 28  ", DayOfWeek.FRIDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-29", " 29  ", DayOfWeek.SATURDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-30", " 30 ◇", DayOfWeek.SUNDAY, true),
        DrawDaysUseCaseTestDayProperties("2018-12-31", " 31  ", DayOfWeek.MONDAY, true),
        DrawDaysUseCaseTestDayProperties("2019-01-01", "  1 ·", DayOfWeek.TUESDAY),
        DrawDaysUseCaseTestDayProperties("2019-01-02", "  2 ·", DayOfWeek.WEDNESDAY),
        DrawDaysUseCaseTestDayProperties("2019-01-03", "  3 ·", DayOfWeek.THURSDAY),
        DrawDaysUseCaseTestDayProperties("2019-01-04", "  4 ·", DayOfWeek.FRIDAY),
        DrawDaysUseCaseTestDayProperties("2019-01-05", "  5 ◈", DayOfWeek.SATURDAY),
        DrawDaysUseCaseTestDayProperties("2019-01-06", "  6 ·", DayOfWeek.SUNDAY)
    )

    private fun getSystemLocalDateAndFirstDayOfWeekWithExpectedCurrentWeekFocusedInitialLocalDate() = Stream.of(
        Arguments.of(of(2022, 2, 24), DayOfWeek.MONDAY, of(2022, 2, 14)),
        Arguments.of(of(2022, 2, 27), DayOfWeek.MONDAY, of(2022, 2, 14)),
        Arguments.of(of(2022, 2, 28), DayOfWeek.MONDAY, of(2022, 2, 21)),
        Arguments.of(of(2022, 2, 28), DayOfWeek.TUESDAY, of(2022, 2, 15)),
        Arguments.of(of(2022, 3, 1), DayOfWeek.TUESDAY, of(2022, 2, 22)),
        Arguments.of(of(2022, 1, 1), DayOfWeek.WEDNESDAY, of(2021, 12, 22)),
        Arguments.of(of(2022, 1, 1), DayOfWeek.SATURDAY, of(2021, 12, 25)),
        Arguments.of(of(2022, 2, 20), DayOfWeek.SUNDAY, of(2022, 2, 13)),
        Arguments.of(of(2022, 2, 25), DayOfWeek.SUNDAY, of(2022, 2, 13)),
        Arguments.of(of(2022, 2, 26), DayOfWeek.SUNDAY, of(2022, 2, 13)),
        Arguments.of(of(2022, 2, 27), DayOfWeek.SUNDAY, of(2022, 2, 20))
    )

    private fun getSystemLocalDateAndFirstDayOfWeekWithExpectedNaturalMonthInitialLocalDate() = Stream.of(
        Arguments.of(of(2018, 1, 26), DayOfWeek.MONDAY, of(2018, 1, 1)),
        Arguments.of(of(2018, 1, 26), DayOfWeek.TUESDAY, of(2017, 12, 26)),
        Arguments.of(of(2018, 1, 26), DayOfWeek.WEDNESDAY, of(2017, 12, 27)),
        Arguments.of(of(2018, 1, 26), DayOfWeek.THURSDAY, of(2017, 12, 28)),
        Arguments.of(of(2018, 1, 26), DayOfWeek.FRIDAY, of(2017, 12, 29)),
        Arguments.of(of(2018, 1, 26), DayOfWeek.SATURDAY, of(2017, 12, 30)),
        Arguments.of(of(2018, 1, 26), DayOfWeek.SUNDAY, of(2017, 12, 31)),
        Arguments.of(of(2005, 2, 19), DayOfWeek.WEDNESDAY, of(2005, 1, 26)),
        Arguments.of(of(2027, 3, 5), DayOfWeek.SUNDAY, of(2027, 2, 28)),
        Arguments.of(of(2099, 4, 30), DayOfWeek.MONDAY, of(2099, 3, 30)),
        Arguments.of(of(2000, 5, 1), DayOfWeek.SATURDAY, of(2000, 4, 29)),
        Arguments.of(of(1998, 6, 2), DayOfWeek.WEDNESDAY, of(1998, 5, 27)),
        Arguments.of(of(1992, 7, 7), DayOfWeek.TUESDAY, of(1992, 6, 30)),
        Arguments.of(of(2018, 8, 1), DayOfWeek.FRIDAY, of(2018, 7, 27)),
        Arguments.of(of(1987, 9, 12), DayOfWeek.FRIDAY, of(1987, 8, 28)),
        Arguments.of(of(2017, 10, 1), DayOfWeek.THURSDAY, of(2017, 9, 28)),
        Arguments.of(of(1000, 11, 12), DayOfWeek.SATURDAY, of(1000, 11, 1)),
        Arguments.of(of(1994, 12, 13), DayOfWeek.THURSDAY, of(1994, 12, 1)),
        Arguments.of(of(2021, 2, 13), DayOfWeek.MONDAY, of(2021, 2, 1)),
        Arguments.of(of(2021, 3, 13), DayOfWeek.MONDAY, of(2021, 3, 1))
    )!!

    private fun getLocalDateAndIncludeDeclinedEventsWithExpectedNumberOfInstances() = Stream.of(
        Arguments.of(of(2018, 11, 26), false, 1),
        Arguments.of(of(2018, 11, 27), false, 0),
        Arguments.of(of(2018, 11, 28), false, 1),
        Arguments.of(of(2018, 11, 29), false, 1),
        Arguments.of(of(2018, 11, 30), false, 0),
        Arguments.of(of(2018, 12, 1), false, 0),
        Arguments.of(of(2018, 12, 2), false, 0),
        Arguments.of(of(2018, 12, 3), false, 1),
        Arguments.of(of(2018, 12, 4), false, 1),
        Arguments.of(of(2018, 12, 5), false, 0),
        Arguments.of(of(2018, 12, 6), false, 3),
        Arguments.of(of(2018, 12, 7), false, 1),
        Arguments.of(of(2018, 12, 8), false, 0),
        Arguments.of(of(2018, 12, 9), false, 0),
        Arguments.of(of(2018, 12, 10), false, 4),
        Arguments.of(of(2018, 12, 11), false, 1),
        Arguments.of(of(2018, 12, 12), false, 0),
        Arguments.of(of(2018, 12, 13), false, 0),
        Arguments.of(of(2018, 12, 14), false, 0),
        Arguments.of(of(2018, 12, 15), false, 0),
        Arguments.of(of(2018, 12, 16), false, 0),
        Arguments.of(of(2018, 12, 17), false, 0),
        Arguments.of(of(2018, 12, 18), false, 0),
        Arguments.of(of(2018, 12, 19), false, 0),
        Arguments.of(of(2018, 12, 20), false, 0),
        Arguments.of(of(2018, 12, 21), false, 0),
        Arguments.of(of(2018, 12, 22), false, 0),
        Arguments.of(of(2018, 12, 23), false, 0),
        Arguments.of(of(2018, 12, 24), false, 0),
        Arguments.of(of(2018, 12, 25), false, 0),
        Arguments.of(of(2018, 12, 26), false, 0),
        Arguments.of(of(2018, 12, 27), false, 1),
        Arguments.of(of(2018, 12, 28), false, 0),
        Arguments.of(of(2018, 12, 29), false, 0),
        Arguments.of(of(2018, 12, 30), false, 5),
        Arguments.of(of(2018, 12, 31), false, 0),
        Arguments.of(of(2019, 1, 1), false, 1),
        Arguments.of(of(2019, 1, 2), false, 1),
        Arguments.of(of(2019, 1, 3), false, 1),
        Arguments.of(of(2019, 1, 4), false, 1),
        Arguments.of(of(2019, 1, 5), false, 7),
        Arguments.of(of(2019, 1, 6), false, 1),
        Arguments.of(of(2018, 11, 26), true, 1),
        Arguments.of(of(2018, 11, 27), true, 0),
        Arguments.of(of(2018, 11, 28), true, 1),
        Arguments.of(of(2018, 11, 29), true, 1),
        Arguments.of(of(2018, 11, 30), true, 0),
        Arguments.of(of(2018, 12, 1), true, 0),
        Arguments.of(of(2018, 12, 2), true, 0),
        Arguments.of(of(2018, 12, 3), true, 1),
        Arguments.of(of(2018, 12, 4), true, 1),
        Arguments.of(of(2018, 12, 5), true, 0),
        Arguments.of(of(2018, 12, 6), true, 3),
        Arguments.of(of(2018, 12, 7), true, 1),
        Arguments.of(of(2018, 12, 8), true, 0),
        Arguments.of(of(2018, 12, 9), true, 0),
        Arguments.of(of(2018, 12, 10), true, 4),
        Arguments.of(of(2018, 12, 11), true, 1),
        Arguments.of(of(2018, 12, 12), true, 0),
        Arguments.of(of(2018, 12, 13), true, 0),
        Arguments.of(of(2018, 12, 14), true, 0),
        Arguments.of(of(2018, 12, 15), true, 0),
        Arguments.of(of(2018, 12, 16), true, 0),
        Arguments.of(of(2018, 12, 17), true, 0),
        Arguments.of(of(2018, 12, 18), true, 1),
        Arguments.of(of(2018, 12, 19), true, 0),
        Arguments.of(of(2018, 12, 20), true, 0),
        Arguments.of(of(2018, 12, 21), true, 0),
        Arguments.of(of(2018, 12, 22), true, 0),
        Arguments.of(of(2018, 12, 23), true, 0),
        Arguments.of(of(2018, 12, 24), true, 0),
        Arguments.of(of(2018, 12, 25), true, 0),
        Arguments.of(of(2018, 12, 26), true, 0),
        Arguments.of(of(2018, 12, 27), true, 1),
        Arguments.of(of(2018, 12, 28), true, 0),
        Arguments.of(of(2018, 12, 29), true, 0),
        Arguments.of(of(2018, 12, 30), true, 5),
        Arguments.of(of(2018, 12, 31), true, 0),
        Arguments.of(of(2019, 1, 1), true, 1),
        Arguments.of(of(2019, 1, 2), true, 2),
        Arguments.of(of(2019, 1, 3), true, 2),
        Arguments.of(of(2019, 1, 4), true, 2),
        Arguments.of(of(2019, 1, 5), true, 8),
        Arguments.of(of(2019, 1, 6), true, 2)
    )!!

    internal data class DrawDaysUseCaseTestProperties(
        val format: Format,
        val focusOnCurrentWeek: Boolean,
        val expectedDayProperties: Stream<DrawDaysUseCaseTestDayProperties>
    )

    internal data class DrawDaysUseCaseTestDayProperties(
        private val day: String,
        val text: String,
        val dayOfWeek: DayOfWeek,
        val isInMonth: Boolean = false,
        val isToday: Boolean = false
    ) {
        fun startOfDay(zoneOffset: ZoneOffset): Instant =
            LocalDateTime.parse("${day}T00:00:00Z", DateTimeFormatter.ISO_ZONED_DATE_TIME).toInstant(zoneOffset)
    }
}
