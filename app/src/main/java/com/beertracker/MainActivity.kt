package com.beertracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.beertracker.ui.AddEditBeerViewModel
import com.beertracker.ui.AddEditScreen
import com.beertracker.ui.OverviewScreen
import com.beertracker.ui.OverviewViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BeerNavHost()
            }
        }
    }
}

@Composable
fun BeerNavHost() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "overview") {
        composable("overview") {
            OverviewScreen(
                viewModel = viewModel(factory = OverviewViewModel.Factory),
                onAddClick = { navController.navigate("edit") },
                onBeerClick = { id -> navController.navigate("detail/$id") },
            )
        }
        composable(
            route = "edit?beerId={beerId}",
            arguments = listOf(navArgument("beerId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }),
        ) { backStackEntry ->
            AddEditScreen(
                viewModel = viewModel(factory = AddEditBeerViewModel.Factory),
                beerId = backStackEntry.arguments?.getString("beerId"),
                onDone = { navController.popBackStack() },
            )
        }
        composable("detail/{beerId}") {
            Text("Detail screen arrives in Task 9")
        }
    }
}
