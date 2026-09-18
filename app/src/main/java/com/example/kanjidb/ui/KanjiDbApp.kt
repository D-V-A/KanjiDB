package com.example.kanjidb.ui

import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.kanjidb.ui.training.TrainingState
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.kanjidb.ui.search.RecommendedState
import com.example.kanjidb.data.dictionary.DictionaryDatabase
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.kanjidb.R
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.kanjidb.ui.about.AboutScreen
import com.example.kanjidb.ui.details.KanjiDetailsScreen
import com.example.kanjidb.ui.details.WordDetailsScreen
import com.example.kanjidb.ui.lists.MyKanjiScreen
import com.example.kanjidb.ui.search.SearchScreen
import com.example.kanjidb.ui.training.TrainingScreen

private const val SEARCH = "search"
private const val ABOUT = "about"
private const val MY_KANJI = "my_kanji"
private const val TRAINING = "training"
private const val KANJI_ID = "kanjiId"
private const val WORD_DETAILS = "word/{entryId}/{written}?sourceKanji={sourceKanji}"
private const val DETAILS = "details/{$KANJI_ID}"

private val topLevelDestinations = listOf(
    SEARCH to R.string.search_title,
    MY_KANJI to R.string.my_kanji_title,
    TRAINING to R.string.training_title
)

@Composable
fun KanjiDbApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val userDao = remember(context) {
        com.example.kanjidb.data.user.UserDatabase.getInstance(context).kanjiStates()
    }
    LaunchedEffect(userDao) { RecommendedState.initialize(DictionaryDatabase(context), userDao) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var exitTrainingTo by rememberSaveable { mutableStateOf<String?>(null) }
    // A restored exit dialog must not outlive the process-only session after process death.
    LaunchedEffect(TrainingState.session == null) {
        if (TrainingState.session == null) exitTrainingTo = null
    }
    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    if (exitTrainingTo != null && TrainingState.session != null) {
        AlertDialog(
            onDismissRequest = { exitTrainingTo = null },
            title = { Text("End training?") },
            text = { Text("Progress for this training session will be lost.") },
            dismissButton = { TextButton(onClick = { exitTrainingTo = null }) { Text("Continue training") } },
            confirmButton = {
                TextButton(enabled = !TrainingState.saving, onClick = {
                    val target = exitTrainingTo
                    exitTrainingTo = null
                    TrainingState.cancel()
                    if (target != null && target != TRAINING) navigateTopLevel(target)
                }) { Text("End training") }
            }
        )
    }
    val openDetails: (String) -> Unit = { kanjiId ->
        navController.navigate("details/${Uri.encode(kanjiId)}") {
            launchSingleTop = true
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (topLevelDestinations.any { it.first == currentRoute }) {
                NavigationBar {
                    topLevelDestinations.forEach { (route, labelResource) ->
                        val label = stringResource(labelResource)
                        NavigationBarItem(
                            selected = currentRoute == route,
                            enabled = !TrainingState.saving,
                            onClick = {
                                if (currentRoute != route) {
                                    if (TrainingState.session != null) exitTrainingTo = route
                                    else navigateTopLevel(route)
                                }
                            },
                            icon = { Text(label.take(1)) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = SEARCH,
            modifier = Modifier.padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .fillMaxSize()
        ) {
            composable(SEARCH) { entry ->
                DisposableEffect(entry) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            entry.savedStateHandle.remove<String>("recommendedDetails")?.let {
                                RecommendedState.returnedFromDetails(it)
                            }
                        }
                    }
                    entry.lifecycle.addObserver(observer)
                    onDispose { entry.lifecycle.removeObserver(observer) }
                }
                SearchScreen(onOpenRecommended = { character ->
                    entry.savedStateHandle["recommendedDetails"] = character
                    openDetails(character)
                }, onOpenDetails = openDetails, onOpenAbout = {
                    navController.navigate(ABOUT) { launchSingleTop = true }
                }, onOpenWord = { entryId, written ->
                    navController.navigate("word/$entryId/${Uri.encode(written)}") {
                        launchSingleTop = true
                    }
                })
            }
            composable(ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
            composable(MY_KANJI) {
                MyKanjiScreen(onOpenDetails = openDetails, userDao = userDao)
            }
            composable(TRAINING) {
                TrainingScreen(userDao = userDao, onRequestExit = { exitTrainingTo = TRAINING })
            }
            composable(
                route = WORD_DETAILS,
                arguments = listOf(
                    navArgument("entryId") { type = NavType.LongType },
                    navArgument("written") { type = NavType.StringType },
                    navArgument("sourceKanji") { type = NavType.StringType; defaultValue = "" }
                )
            ) { entry ->
                val arguments = requireNotNull(entry.arguments)
                WordDetailsScreen(
                    entryId = arguments.getLong("entryId"),
                    written = requireNotNull(arguments.getString("written")),
                    sourceKanji = requireNotNull(arguments.getString("sourceKanji"))
                )
            }
            composable(
                route = DETAILS,
                arguments = listOf(navArgument(KANJI_ID) { type = NavType.StringType })
            ) { entry ->
                val kanjiId = requireNotNull(entry.arguments?.getString(KANJI_ID))
                KanjiDetailsScreen(
                    kanjiId = kanjiId,
                    userDao = userDao,
                    onOpenWord = { entryId, written ->
                        navController.navigate("word/$entryId/${Uri.encode(written)}?sourceKanji=${Uri.encode(kanjiId)}") {
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    }
}