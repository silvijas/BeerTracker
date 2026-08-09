package com.beertracker.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beertracker.R
import com.beertracker.ui.theme.BeerTrackerTheme

/**
 * Shows a grade as filled beer cans out of ten, in two rows of five.
 * [size] is the total height of the can grid.
 */
@Composable
fun GradeMark(
    grade: Int?,
    tried: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
) {
    if (grade != null) {
        val description = stringResource(R.string.grade_value, grade)
        val gap = 3.dp
        val canHeight = (size - gap) / 2
        Column(
            modifier = modifier.semantics { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            listOf(1..5, 6..10).forEach { slots ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    slots.forEach { slot ->
                        BeerCan(filled = slot <= grade, height = canHeight)
                    }
                }
            }
        }
    } else {
        CompactGradeState(
            textRes = if (tried) R.string.no_grade else R.string.not_tried,
            modifier = modifier,
        )
    }
}

@Composable
private fun CompactGradeState(
    @StringRes textRes: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = stringResource(textRes),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GradeMarkPreview() {
    BeerTrackerTheme {
        GradeMark(grade = 9, tried = true)
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GradeMarkDarkPreview() {
    BeerTrackerTheme {
        GradeMark(grade = null, tried = false)
    }
}
