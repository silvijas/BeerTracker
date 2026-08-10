package com.beertracker.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beertracker.R
import com.beertracker.ui.theme.BeerTrackerTheme

/**
 * Shows a grade as its number next to one filled beer can. [size] is the
 * can's height.
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
        Row(
            modifier = modifier.clearAndSetSemantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(grade.toString(), style = MaterialTheme.typography.titleLarge)
            BeerCan(filled = true, height = size)
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
        GradeMark(grade = 4, tried = true)
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GradeMarkDarkPreview() {
    BeerTrackerTheme {
        GradeMark(grade = null, tried = false)
    }
}
