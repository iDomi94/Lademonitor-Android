package com.dominiqueherbrigpersonalteam.lademonitor.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ElectricCar
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppMode
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Whether the introduction is open again from the settings. Shown by LademonitorRoot on top of
 * everything (like the fullScreenCover on iOS), so it covers the bottom bar too.
 */
object OnboardingReplay {
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible
    fun show() { _visible.value = true }
    fun hide() { _visible.value = false }
}

/** First launch: the last page leads on to the mode selection. Replay: it only closes. */
enum class OnboardingContext { FIRST_LAUNCH, REPLAY }

private data class Point(val icon: ImageVector, @param:StringRes val text: Int)

private data class FeaturePage(
    val icon: ImageVector,
    @param:StringRes val title: Int,
    @param:StringRes val intro: Int,
    val points: List<Point>
)

/**
 * Introduction to the main features. Port of the iOS `OnboardingView` - same pages and texts, any
 * change goes into both apps. Only the capture page differs: app-icon shortcut instead of Siri.
 */
private val featurePages = listOf(
    FeaturePage(
        Icons.Filled.ElectricCar, R.string.onboarding_welcome_title, R.string.onboarding_welcome_intro,
        listOf(
            Point(Icons.Filled.Euro, R.string.onboarding_welcome_p1),
            Point(Icons.Filled.Speed, R.string.onboarding_welcome_p2),
            Point(Icons.Filled.Lock, R.string.onboarding_welcome_p3),
        )
    ),
    FeaturePage(
        Icons.Filled.Bolt, R.string.onboarding_capture_title, R.string.onboarding_capture_intro,
        listOf(
            Point(Icons.Filled.AddCircle, R.string.onboarding_capture_p1),
            Point(Icons.Filled.Place, R.string.onboarding_capture_p2),
            Point(Icons.Filled.TouchApp, R.string.onboarding_capture_p3),
            Point(Icons.Filled.Sensors, R.string.onboarding_capture_p4),
        )
    ),
    FeaturePage(
        Icons.Filled.BarChart, R.string.onboarding_stats_title, R.string.onboarding_stats_intro,
        listOf(
            Point(Icons.Filled.PieChart, R.string.onboarding_stats_p1),
            Point(Icons.Filled.CreditCard, R.string.onboarding_stats_p2),
            Point(Icons.Filled.WbSunny, R.string.onboarding_stats_p3),
            Point(Icons.Filled.Map, R.string.onboarding_stats_p4),
        )
    ),
    FeaturePage(
        Icons.Filled.DirectionsCar, R.string.onboarding_car_title, R.string.onboarding_car_intro,
        listOf(
            Point(Icons.Filled.Euro, R.string.onboarding_car_p1),
            Point(Icons.Filled.Notifications, R.string.onboarding_car_p2),
            Point(Icons.Filled.LocalGasStation, R.string.onboarding_car_p3),
        )
    ),
    FeaturePage(
        Icons.Filled.Build, R.string.onboarding_tools_title, R.string.onboarding_tools_intro,
        listOf(
            Point(Icons.Filled.Euro, R.string.onboarding_tools_p1),
            Point(Icons.Filled.Explore, R.string.onboarding_tools_p2),
            Point(Icons.Filled.CalendarMonth, R.string.onboarding_tools_p3),
            Point(Icons.Filled.Work, R.string.onboarding_tools_p4),
        )
    ),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(context: OnboardingContext, onFinish: () -> Unit) {
    val pageCount = featurePages.size + 1
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pageCount - 1

    // Replay: "back" closes it. First launch: back has nowhere to go, leave the system default.
    if (context == OnboardingContext.REPLAY) BackHandler(onBack = onFinish)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isLastPage) {
                    TextButton(onClick = onFinish) {
                        Text(
                            stringResource(
                                if (context == OnboardingContext.FIRST_LAUNCH) R.string.onboarding_skip
                                else R.string.onboarding_close
                            )
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) { index ->
                if (index < featurePages.size) FeaturePageContent(featurePages[index])
                else ModesPageContent(context)
            }

            PageDots(count = pageCount, current = pagerState.currentPage)

            Button(
                onClick = {
                    if (isLastPage) onFinish()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    stringResource(
                        when {
                            !isLastPage -> R.string.onboarding_next
                            context == OnboardingContext.FIRST_LAUNCH -> R.string.onboarding_choose_mode
                            else -> R.string.onboarding_done
                        }
                    ),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(8.dp)
                    .background(
                        if (i == current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape
                    )
            )
        }
    }
}

/** Keeps the text column readable on tablets and unfolded foldables. */
@Composable
private fun PageColumn(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) { content() }
    }
}

@Composable
private fun FeaturePageContent(page: FeaturePage) {
    PageColumn {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Icon(page.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        }
        Text(stringResource(page.title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(page.intro), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        page.points.forEach { point ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(point.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.size(14.dp))
                Text(stringResource(point.text), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Last page: the three ways to use the app side by side. */
@Composable
private fun ModesPageContent(context: OnboardingContext) {
    val appMode by AppSettings.appMode.collectAsStateWithLifecycle()
    val hosting by AppSettings.serverHosting.collectAsStateWithLifecycle()
    val replay = context == OnboardingContext.REPLAY

    PageColumn {
        Text(stringResource(R.string.onboarding_modes_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.onboarding_modes_intro), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

        ModeCard(
            Icons.Filled.PhoneAndroid, R.string.onboarding_mode_local_title,
            isCurrent = replay && appMode == AppMode.LOCAL_ONLY,
            points = listOf(
                R.string.onboarding_mode_local_p1,
                R.string.onboarding_mode_local_p2,
                R.string.onboarding_mode_local_p3,
            )
        )
        ModeCard(
            Icons.Filled.Dns, R.string.onboarding_mode_server_title,
            isCurrent = replay && appMode == AppMode.SERVER && hosting == AppSettings.ServerHosting.SELF_HOSTED,
            points = listOf(
                R.string.onboarding_mode_server_p1,
                R.string.onboarding_mode_server_p2,
                R.string.onboarding_mode_server_p3,
                R.string.onboarding_mode_server_p4,
            )
        )
        ModeCard(
            Icons.Filled.Cloud, R.string.onboarding_mode_cloud_title,
            isCurrent = replay && appMode == AppMode.SERVER && hosting == AppSettings.ServerHosting.CLOUD,
            points = listOf(
                R.string.onboarding_mode_cloud_p1,
                R.string.onboarding_mode_cloud_p2,
                R.string.onboarding_mode_cloud_p3,
            )
        )
        Text(
            stringResource(R.string.onboarding_modes_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ModeCard(icon: ImageVector, @StringRes title: Int, isCurrent: Boolean, points: List<Int>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isCurrent) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.size(12.dp))
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (isCurrent) {
                    Text(
                        stringResource(R.string.onboarding_current),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            points.forEach { res ->
                Row(verticalAlignment = Alignment.Top) {
                    Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(res), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
