// Copyright (c) 2016, Miquel Martí <miquelmarti111@gmail.com>
// See LICENSE for licensing information
package cat.mvmike.minimalcalendarwidget.domain.intent

import cat.mvmike.minimalcalendarwidget.BaseTest
import cat.mvmike.minimalcalendarwidget.domain.intent.AutoUpdate.ALARM_ID
import cat.mvmike.minimalcalendarwidget.domain.intent.AutoUpdate.INTERVAL_MILLIS
import io.mockk.confirmVerified
import io.mockk.justRun
import io.mockk.verify
import java.time.Instant
import org.junit.jupiter.api.Test

internal class AutoUpdateTest : BaseTest() {

    @Test
    fun setAlarm_shouldSetRepeatingAlarm() {
        val instant = Instant.now()
        mockGetSystemInstant(instant)
        justRun {
            systemResolver.setRepeatingAlarm(
                context = context,
                alarmId = ALARM_ID,
                firstTriggerMillis = instant.toEpochMilli() + INTERVAL_MILLIS,
                intervalMillis = INTERVAL_MILLIS
            )
        }

        AutoUpdate.setAlarm(context)

        verify {
            systemResolver.setRepeatingAlarm(
                context = context,
                alarmId = ALARM_ID,
                firstTriggerMillis = instant.toEpochMilli() + INTERVAL_MILLIS,
                intervalMillis = INTERVAL_MILLIS
            )
        }
        confirmVerified(context)
    }

    @Test
    fun cancelAlarm_shouldCancelAlarm() {
        justRun {
            systemResolver.cancelRepeatingAlarm(
                context = context,
                alarmId = ALARM_ID
            )
        }

        AutoUpdate.cancelAlarm(context)

        verify {
            systemResolver.cancelRepeatingAlarm(
                context = context,
                alarmId = ALARM_ID
            )
        }
        confirmVerified(context)
    }
}
