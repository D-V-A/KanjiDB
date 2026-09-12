package com.example.kanjidb.ui

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.kanjidb.ui.details.KanjiDetailsScreen
import com.example.kanjidb.ui.lists.MyKanjiScreen
import com.example.kanjidb.ui.search.SearchScreen
import com.example.kanjidb.ui.training.TrainingScreen

private const val SEARCH = "search"
private const val MY_KANJI = "my_kanji"
private const val TRAINING = "training"
private const val KANJI_ID = "kanjiId"
private const val DETAILS = "details/{$KANJI_ID}"

private val topLevelDestinations = listOf(
    SEARCH to "Search",
    MY_KANJI to "My Kanji",
    TRAINING to "Training"
)

@Composable
fun KanjiDbApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
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
                    topLevelDestinations.forEach { (route, label) ->
                        NavigationBarItem(
                            selected = currentRoute == route,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
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
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) {
            composable(SEARCH) {
                SearchScreen(onOpenDetails = openDetails)
            }
            composable(MY_KANJI) {
                MyKanjiScreen(onOpenDetails = { openDetails("mountain") })
            }
            composable(TRAINING) {
                TrainingScreen(onOpenDetails = { openDetails("mountain") })
            }
            composable(
                route = DETAILS,
                arguments = listOf(navArgument(KANJI_ID) { type = NavType.StringType })
            ) { entry ->
                KanjiDetailsScreen(
                    kanjiId = requireNotNull(entry.arguments?.getString(KANJI_ID))
                )
            }
        }
    }
}