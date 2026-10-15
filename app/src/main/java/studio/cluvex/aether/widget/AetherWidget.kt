package studio.cluvex.aether.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.unit.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import studio.cluvex.aether.R
import studio.cluvex.aether.core.AetherRuntime
import studio.cluvex.aether.core.AetherState
import studio.cluvex.aether.core.AetherToggle
import studio.cluvex.aether.ui.formatBytes
import java.time.Duration
import java.time.Instant

/**
 * The 4x2 home-screen widget: Pixel-style card with the app mark, live
 * status line, momentary speeds and one big circular power button that
 * toggles the tunnel without opening the app.
 */
class AetherWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = AetherRuntime.state.value
        val throughput = AetherRuntime.throughput.value
        provideContent {
            WidgetContent(context, state, throughput)
        }
    }
}

@Composable
private fun WidgetContent(
    context: Context,
    state: AetherState,
    throughput: AetherRuntime.Throughput,
) {
    val running = state is AetherState.Connected || state is AetherState.Starting || state is AetherState.Stopping

    Box(
        GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(R.color.widget_bg))
            .cornerRadius(24.dp)
            .padding(16.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            InfoColumn(GlanceModifier.defaultWeight(), context, state, throughput)
            Spacer(GlanceModifier.size(14.dp))
            PowerButton(context, running)
        }
    }
}

@Composable
private fun InfoColumn(
    modifier: GlanceModifier,
    context: Context,
    state: AetherState,
    throughput: AetherRuntime.Throughput,
) {
    val connected = state is AetherState.Connected
    val status = when (state) {
        is AetherState.Idle -> context.getString(R.string.state_idle)
        is AetherState.Starting -> when (state.phase) {
            AetherState.Starting.Phase.PREPARING -> context.getString(R.string.state_preparing)
            AetherState.Starting.Phase.SCANNING -> context.getString(R.string.state_scanning)
            AetherState.Starting.Phase.CONNECTING -> context.getString(R.string.state_connecting)
        }
        is AetherState.Connected -> context.getString(R.string.state_connected) +
            " · " + formatUptime(state.since)
        AetherState.Stopping -> context.getString(R.string.state_stopping)
        is AetherState.Failed -> context.getString(R.string.state_failed)
    }

    Column(
        modifier = modifier.fillMaxHeight(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_ripple),
                contentDescription = null,
                modifier = GlanceModifier.size(20.dp),
            )
            Spacer(GlanceModifier.size(6.dp))
            Text(
                text = context.getString(R.string.app_name),
                style = TextStyle(
                    color = ColorProvider(R.color.widget_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        Spacer(GlanceModifier.size(4.dp))
        Text(
            text = status,
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(if (connected) R.color.widget_on else R.color.widget_dim),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        if (connected) {
            Spacer(GlanceModifier.size(3.dp))
            Text(
                text = context.getString(
                    R.string.home_throughput,
                    formatBytes(throughput.rateDown),
                    formatBytes(throughput.rateUp),
                ),
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(R.color.widget_dim),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                ),
            )
        }
    }
}

@Composable
private fun PowerButton(context: Context, running: Boolean) {
    val glyph = if (running) R.drawable.ic_power else R.drawable.ic_power_idle
    Box(
        GlanceModifier
            .size(72.dp)
            .background(ColorProvider(if (running) R.color.widget_btn_run else R.color.widget_btn_idle))
            .cornerRadius(36.dp)
            .clickable(actionRunCallback<ToggleAction>())
            .padding(22.dp),
    ) {
        Image(
            provider = ImageProvider(glyph),
            contentDescription = context.getString(
                if (running) R.string.widget_tap_disconnect else R.string.widget_tap_connect,
            ),
            modifier = GlanceModifier.fillMaxSize(),
        )
    }
}

/** One tap on the widget = toggle the tunnel, exactly like the app button. */
class ToggleAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        AetherToggle.toggle(context)
    }
}

/** Static entry point the system binds the widget to. */
class AetherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AetherWidget()
}

/**
 * Re-composes every placed widget from the current runtime state. Called by
 * the service whenever the tunnel state or the live speeds move.
 */
suspend fun pushWidgetUpdate(context: Context) {
    runCatching { AetherWidget().updateAll(context) }
}

private fun formatUptime(since: Instant): String {
    val seconds = Duration.between(since, Instant.now()).seconds
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
