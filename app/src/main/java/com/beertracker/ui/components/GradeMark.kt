package com.beertracker.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.beertracker.R
import com.beertracker.ui.theme.BeerTrackerTheme

@Composable
fun GradeMark(
    grade: Int?,
    tried: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
) {
    if (grade != null) {
        val description = stringResource(R.string.grade_value, grade)
        Surface(
            modifier = modifier
                .defaultMinSize(minWidth = size, minHeight = size)
                .clip(CircleShape)
                .semantics { contentDescription = description },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = grade.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
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
