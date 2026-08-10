package com.beertracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.beertracker.ui.AddEditBeerViewModel
import com.beertracker.ui.AddEditScreen
import com.beertracker.ui.CatalogRefreshViewModel
import com.beertracker.ui.DetailScreen
import com.beertracker.ui.DetailViewModel
import com.beertracker.ui.OverviewScreen
import com.beertracker.ui.OverviewViewModel
import com.beertracker.ui.catalog.CatalogBrowserScreen
import com.beertracker.ui.catalog.CatalogBrowserViewModel
import com.beertracker.ui.scan.ScanScreen
import com.beertracker.ui.scan.ScanViewModel
import com.beertracker.ui.theme.BeerTrackerTheme
import com.beertracker.ui.ThemeViewModel
import com.beertracker.domain.ThemeMode
import com.beertracker.domain.isDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModel.Factory)
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            BeerTrackerTheme(darkTheme = themeMode.isDarkTheme(isSystemInDarkTheme())) {
                BeerNavHost(
                    themeMode = themeMode,
                    onSetThemeMode = themeViewModel::setThemeMode,
                )
            }
        }
    }
}

@Composable
fun BeerNavHost(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onSetThemeMode: (ThemeMode) -> Unit = {},
) {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "overview") {
        composable("overview") {
            OverviewScreen(
                viewModel = viewModel(factory = OverviewViewModel.Factory),
                catalogViewModel = viewModel(factory = CatalogRefreshViewModel.Factory),
                onAddClick = { navController.navigate("edit") },
                onBeerClick = { id -> navController.navigate("detail/$id") },
                onScanClick = { navController.navigate("scan") },
                onCatalogClick = { navController.navigate("catalog") },
                themeMode = themeMode,
                onSetThemeMode = onSetThemeMode,
            )
        }
        composable(
            route = "edit?beerId={beerId}&prefillArticle={prefillArticle}",
            arguments = listOf(
                navArgument("beerId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("prefillArticle") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            AddEditScreen(
                viewModel = viewModel(factory = AddEditBeerViewModel.Factory),
                beerId = backStackEntry.arguments?.getString("beerId"),
                prefillArticle = backStackEntry.arguments?.getString("prefillArticle"),
                onDone = { navController.popBackStack() },
            )
        }
        composable("scan") {
            ScanScreen(
                viewModel = viewModel(factory = ScanViewModel.Factory),
                onFound = { articleNumber ->
                    navController.navigate("edit?prefillArticle=$articleNumber") {
                        popUpTo("scan") { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable("catalog") {
            CatalogBrowserScreen(
                viewModel = viewModel(factory = CatalogBrowserViewModel.Factory),
                onAddProduct = { articleNumber ->
                    navController.navigate("edit?prefillArticle=$articleNumber")
                },
                onOpenBeer = { id -> navController.navigate("detail/$id") },
                onBack = { navController.popBackStack() },
            )
        }
        composable("detail/{beerId}") { backStackEntry ->
            val beerId = backStackEntry.arguments?.getString("beerId") ?: return@composable
            DetailScreen(
                viewModel = viewModel(factory = DetailViewModel.factory(beerId)),
                onEdit = { id -> navController.navigate("edit?beerId=$id") },
                onBreweryClick = { },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
