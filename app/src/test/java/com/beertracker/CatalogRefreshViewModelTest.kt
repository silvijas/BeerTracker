package com.beertracker

import android.app.Application
import com.beertracker.domain.CatalogRefresher
import com.beertracker.domain.CatalogStatus
import com.beertracker.domain.RefreshResult
import com.beertracker.ui.CatalogRefreshUiState
import com.beertracker.ui.CatalogRefreshViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CatalogRefreshViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        repository: FakeCatalogRepository = FakeCatalogRepository(),
        refresher: FakeCatalogRefresher = FakeCatalogRefresher(),
    ) = CatalogRefreshViewModel(repository, refresher)

    private fun TestScope.collectingStatus(vm: CatalogRefreshViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.status.collect() }
    }

    @Test
    fun `starts idle`() {
        assertEquals(CatalogRefreshUiState.Idle, viewModel().refreshState.value)
    }

    @Test
    fun `refresh lands on done with the success result`() = runTest {
        val refresher = FakeCatalogRefresher().apply {
            result = RefreshResult.Success(beerCount = 1534, refreshedUtc = 42L)
        }
        val vm = viewModel(refresher = refresher)

        vm.refresh()

        assertEquals(
            CatalogRefreshUiState.Done(RefreshResult.Success(beerCount = 1534, refreshedUtc = 42L)),
            vm.refreshState.value,
        )
        assertEquals(1, refresher.refreshCalls)
    }

    @Test
    fun `refresh lands on done with the failure result`() = runTest {
        val refresher = FakeCatalogRefresher().apply {
            result = RefreshResult.Failure("Could not reach the Systembolaget catalog")
        }
        val vm = viewModel(refresher = refresher)

        vm.refresh()

        assertEquals(
            CatalogRefreshUiState.Done(
                RefreshResult.Failure("Could not reach the Systembolaget catalog"),
            ),
            vm.refreshState.value,
        )
    }

    @Test
    fun `an unexpected exception from refresh lands on the calm failure state`() = runTest {
        val throwingRefresher = object : CatalogRefresher {
            override suspend fun refresh(): RefreshResult = throw RuntimeException("boom")
        }
        val vm = CatalogRefreshViewModel(FakeCatalogRepository(), throwingRefresher)

        vm.refresh()

        assertEquals(
            CatalogRefreshUiState.Done(
                RefreshResult.Failure("Could not reach the Systembolaget catalog"),
            ),
            vm.refreshState.value,
        )
    }

    @Test
    fun `refresh while refreshing is ignored`() = runTest {
        val refresher = FakeCatalogRefresher().apply { gate = CompletableDeferred() }
        val vm = viewModel(refresher = refresher)

        vm.refresh()
        assertEquals(CatalogRefreshUiState.Refreshing, vm.refreshState.value)
        vm.refresh()
        refresher.gate?.complete(Unit)

        assertEquals(1, refresher.refreshCalls)
    }

    @Test
    fun `acknowledging the result returns to idle`() = runTest {
        val vm = viewModel()
        vm.refresh()
        vm.acknowledgeResult()
        assertEquals(CatalogRefreshUiState.Idle, vm.refreshState.value)
    }

    @Test
    fun `status mirrors the repository`() = runTest {
        val repository = FakeCatalogRepository()
        val vm = viewModel(repository = repository)
        collectingStatus(vm)

        repository.status.value = CatalogStatus(beerCount = 7, lastRefreshUtc = 1L)

        assertEquals(CatalogStatus(beerCount = 7, lastRefreshUtc = 1L), vm.status.value)
    }
}
