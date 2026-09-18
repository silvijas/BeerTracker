package com.beertracker.ui.scan

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.beertracker.R

/** The two things the camera can read: a shelf label's number, or the text on a can. */
enum class ScanMode { SHELF_LABEL, CAN }

/**
 * The switch at the top of both scan screens. Selecting the other segment
 * calls [onSelect]; the caller navigates. Tapping the already selected
 * segment is ignored so a double tap cannot bounce between routes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScanModeSwitch(
    selected: ScanMode,
    onSelect: (ScanMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        ScanMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { if (mode != selected) onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ScanMode.entries.size),
                label = { Text(stringResource(mode.labelRes)) },
            )
        }
    }
}

private val ScanMode.labelRes: Int
    get() = when (this) {
        ScanMode.SHELF_LABEL -> R.string.scan_mode_shelf_label
        ScanMode.CAN -> R.string.scan_mode_can
    }
