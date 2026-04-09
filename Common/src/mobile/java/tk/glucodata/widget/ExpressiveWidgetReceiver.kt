package tk.glucodata.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class ExpressiveWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ExpressiveAppWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "tk.glucodata.action.GLUCOSE_UPDATE") {
            updateAll(context)
        }
    }
    
    companion object {
        private val scope = MainScope()
        
        @JvmStatic

        fun updateAll(context: Context) {
            scope.launch {
                val manager = androidx.glance.appwidget.GlanceAppWidgetManager(context)
                val widget = ExpressiveAppWidget()
                val glanceIds = manager.getGlanceIds(ExpressiveAppWidget::class.java)
                glanceIds.forEach { glanceId ->
                    widget.update(context, glanceId)
                }
            }
        }
    }
}
