package io.github.samirakhmedov.predictiveback.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.NavigationBackHandler
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberNavigationEventState
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import io.github.samirakhmedov.predictiveback.PredictiveBackSurface
import io.github.samirakhmedov.predictiveback.rememberPredictiveBack

data object Home : NavKey

data object Detail : NavKey

data object Settings : NavKey

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SampleNavHost() }
    }
}

@Composable
private fun SampleNavHost() {
    val darkTheme = isSystemInDarkTheme()
    val containerColor = if (darkTheme) Color.Black else Color.White
    val contentColor = if (darkTheme) Color.White else Color.Black
    val predictiveBack = rememberPredictiveBack()
    val backStack = remember { mutableStateListOf<NavKey>(Home) }
    val onBack: () -> Unit = { backStack.removeLastOrNull() }
    val onNavigate: (NavKey) -> Unit = { key -> backStack.add(key) }
    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
            predictiveBack.entryDecorator
        ),
        entryProvider = entryProvider {
            entry<Home> {
                PredictiveBackSurface(containerColor) {
                    SampleScreen("Home: tap to open Detail", contentColor) {
                        onNavigate(Detail)
                    }
                }
            }
            entry<Detail> {
                PredictiveBackSurface(containerColor) {
                    SampleScreen("Detail: tap to open Settings", contentColor) {
                        onNavigate(Settings)
                    }
                }
            }
            entry<Settings> {
                PredictiveBackSurface(containerColor) {
                    SampleScreen("Settings: tap to return Home", contentColor) {
                        onNavigate(Home)
                    }
                }
            }
        }
    )
    val sceneStrategies = remember { listOf(SinglePaneSceneStrategy<NavKey>()) }
    val sceneState = rememberSceneState(entries, sceneStrategies, onBack = onBack)
    val navState = rememberNavigationEventState(sceneState)
    SideEffect { predictiveBack.attach(navState) }
    NavigationBackHandler(sceneState, navState, onBackCompleted = onBack)
    NavDisplay(
        sceneState = sceneState,
        navigationEventState = navState,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = predictiveBack.transitionSpec,
        popTransitionSpec = predictiveBack.popTransitionSpec,
        predictivePopTransitionSpec = predictiveBack.predictivePopTransitionSpec
    )
}

@Composable
private fun SampleScreen(label: String, textColor: Color, onTap: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        BasicText(text = label, style = TextStyle(color = textColor, fontSize = 24.sp))
    }
}
