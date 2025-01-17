// Copyright (c) 2016, Miquel Martí <miquelmarti111@gmail.com>
// See LICENSE for licensing information
package cat.mvmike.minimalcalendarwidget.application.system

import cat.mvmike.minimalcalendarwidget.BaseTest
import cat.mvmike.minimalcalendarwidget.domain.configuration.clearAllConfiguration
import cat.mvmike.minimalcalendarwidget.domain.intent.AutoUpdate
import io.mockk.justRun
import io.mockk.mockkObject
import io.mockk.verify
import org.junit.jupiter.api.Test

internal class DisableWidgetUseCaseTest : BaseTest() {

    @Test
    fun shouldClearAllConfigurationAndCancelAutoUpdate() {
        mockkObject(AutoUpdate)

        justRun { clearAllConfiguration(context) }
        justRun { AutoUpdate.cancel(context) }

        DisableWidgetUseCase.execute(context)

        verify { clearAllConfiguration(context) }
        verify { AutoUpdate.cancel(context) }
    }
}
