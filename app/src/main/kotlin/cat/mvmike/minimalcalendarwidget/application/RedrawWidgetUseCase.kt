// Copyright (c) 2016, Miquel Martí <miquelmarti111@gmail.com>
// See LICENSE for licensing information
package cat.mvmike.minimalcalendarwidget.application

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import cat.mvmike.minimalcalendarwidget.MonthWidget
import cat.mvmike.minimalcalendarwidget.R
import cat.mvmike.minimalcalendarwidget.domain.component.DaysHeaderService
import cat.mvmike.minimalcalendarwidget.domain.component.DaysService
import cat.mvmike.minimalcalendarwidget.domain.component.LayoutService
import cat.mvmike.minimalcalendarwidget.domain.component.MonthAndYearHeaderService
import cat.mvmike.minimalcalendarwidget.domain.configuration.PercentageConfigurationItem
import cat.mvmike.minimalcalendarwidget.domain.intent.ActionableView.ConfigurationIcon
import cat.mvmike.minimalcalendarwidget.domain.intent.ActionableView.MonthAndYearHeader

object RedrawWidgetUseCase {

    fun execute(
        context: Context
    ) {
        val name = ComponentName(context, MonthWidget::class.java)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        execute(
            context = context,
            appWidgetManager = appWidgetManager,
            appWidgetIds = appWidgetManager.getAppWidgetIds(name)
        )
    }

    fun execute(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) = appWidgetIds.forEach { appWidgetId ->
        execute(
            context = context,
            appWidgetManager = appWidgetManager,
            appWidgetId = appWidgetId,
        )
    }

    fun execute(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) = runCatching {
        val widgetRemoteView = RemoteViews(context.packageName, R.layout.widget)
        widgetRemoteView.removeAllViews(R.id.calendar_days_layout)

        ConfigurationIcon.addListener(context, widgetRemoteView)
        MonthAndYearHeader.addListener(context, widgetRemoteView)

        val textSize = PercentageConfigurationItem.WidgetTextSize.get(context)
        LayoutService.draw(
            context = context,
            widgetRemoteView = widgetRemoteView
        )
        MonthAndYearHeaderService.draw(
            context = context,
            widgetRemoteView = widgetRemoteView,
            textSize = textSize
        )
        DaysHeaderService.draw(
            context = context,
            widgetRemoteView = widgetRemoteView,
            textSize = textSize
        )
        DaysService.draw(
            context = context,
            widgetRemoteView = widgetRemoteView,
            textSize = textSize
        )

        appWidgetManager.updateAppWidget(appWidgetId, widgetRemoteView)
    }
}
