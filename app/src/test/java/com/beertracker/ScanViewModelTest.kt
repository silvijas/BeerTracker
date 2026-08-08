package com.beertracker

import com.beertracker.ui.scan.ScanUiState
import com.beertracker.ui.scan.ScanViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScanViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun catalogWithSample() = FakeCatalogRepository().apply { add(catalogProduct()) }

    @Test
    fun `starts idle`() {
        assertEquals(ScanUiState.Idle, ScanViewModel(catalogWithSample()).uiState.value)
    }

    @Test
    fun `detected text with a known number settles on found`() = runTest {
        val vm = ScanViewModel(catalogWithSample())
        vm.onTextDetected("Omnipollo Nr 13245 29:90")
        val state = vm.uiState.value
        assertTrue(state is ScanUiState.Found)
        assertEquals("1324515", (state as ScanUiState.Found).product.articleNumber)
    }

    @Test
    fun `detected text with unknown numbers stays idle instead of reporting a miss`() = runTest {
        val vm = ScanViewModel(catalogWithSample())
        vm.onTextDetected("Nr 99999")
        assertEquals(ScanUiState.Idle, vm.uiState.value)
    }

    @Test
    fun `the same frame text is not looked up twice`() = runTest {
        val catalog = FakeCatalogRepository()
        val vm = ScanViewModel(catalog)
        vm.onTextDetected("Nr 99999")
        vm.onTextDetected("Nr 99999")
        assertEquals(1, catalog.lookups)
    }

    @Test
    fun `once found further camera text is ignored`() = runTest {
        val catalog = catalogWithSample().apply {
            add(catalogProduct(articleNumber = "1000501", articleNumberShort = "10005", name = "Other"))
        }
        val vm = ScanViewModel(catalog)
        vm.onTextDetected("13245")
        vm.onTextDetected("10005")
        assertEquals("1324515", (vm.uiState.value as ScanUiState.Found).product.articleNumber)
    }

    @Test
    fun `manual lookup after found leaves the found product unchanged`() = runTest {
        val catalog = catalogWithSample().apply {
            add(catalogProduct(articleNumber = "1000501", articleNumberShort = "10005", name = "Other"))
        }
        val vm = ScanViewModel(catalog)
        vm.onTextDetected("13245")
        assertTrue(vm.uiState.value is ScanUiState.Found)
        vm.onManualLookup("10005")
        assertEquals("1324515", (vm.uiState.value as ScanUiState.Found).product.articleNumber)
    }

    @Test
    fun `manual lookup reports a miss with the typed number`() = runTest {
        val vm = ScanViewModel(catalogWithSample())
        vm.onManualLookup(" 99999 ")
        assertEquals(ScanUiState.NotFound("99999"), vm.uiState.value)
    }

    @Test
    fun `manual lookup finds by short number`() = runTest {
        val vm = ScanViewModel(catalogWithSample())
        vm.onManualLookup("13245")
        assertTrue(vm.uiState.value is ScanUiState.Found)
    }

    @Test
    fun `blank manual input does nothing`() = runTest {
        val catalog = catalogWithSample()
        val vm = ScanViewModel(catalog)
        vm.onManualLookup("   ")
        assertEquals(ScanUiState.Idle, vm.uiState.value)
        assertEquals(0, catalog.lookups)
    }

    @Test
    fun `scan again returns to idle and allows rechecking old numbers`() = runTest {
        val catalog = catalogWithSample()
        val vm = ScanViewModel(catalog)
        vm.onManualLookup("99999")
        assertEquals(ScanUiState.NotFound("99999"), vm.uiState.value)
        vm.scanAgain()
        assertEquals(ScanUiState.Idle, vm.uiState.value)
        vm.onTextDetected("13245")
        assertTrue(vm.uiState.value is ScanUiState.Found)
    }
}
