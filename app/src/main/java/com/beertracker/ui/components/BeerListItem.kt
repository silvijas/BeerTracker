package com.beertracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.beertracker.R
import com.beertracker.domain.TriedBeer
import com.beertracker.ui.theme.BeerTrackerSpacing
import com.beertracker.ui.theme.BeerTrackerTheme

@Composable
fun BeerListItem(
    beer: TriedBeer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitle = beerListSubtitle(beer.brewery, beer.type)
    val gradeDescription = when {
        beer.grade != null -> stringResource(R.string.grade_value, beer.grade)
        beer.tried -> stringResource(R.string.no_grade)
        else -> stringResource(R.string.not_tried)
    }
    val flagsDescription = buildString {
        if (beer.favourite) append(stringResource(R.string.beer_item_favourite))
        if (beer.buyAgain) append(stringResource(R.string.beer_item_buy_again))
    }
    val description = if (subtitle == null) {
        stringResource(
            R.string.beer_item_description_no_subtitle,
            beer.name,
            gradeDescription,
            flagsDescription,
        )
    } else {
        stringResource(
            R.string.beer_item_description,
            beer.name,
            subtitle,
            gradeDescription,
            flagsDescription,
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
        horizontalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = beer.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (beer.favourite) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
                if (beer.buyAgain) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(Modifier.size(4.dp))
        GradeMark(grade = beer.grade, tried = beer.tried, size = 48.dp)
    }
}

internal fun beerListSubtitle(brewery: String, type: String): String? =
    listOf(brewery, type)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString(", ")
        .ifEmpty { null }

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun BeerListItemPreview() {
    BeerTrackerTheme {
        BeerListItem(
            beer = TriedBeer(
                id = "preview",
                name = "A very long winter cellar lager name",
                brewery = "Nynäshamns Ångbryggeri",
                type = "Lager",
                alcoholPercent = 5.2,
                volumeMl = 330,
                price = 24.9,
                grade = 9,
                tried = true,
                note = "",
                aftertaste = "",
                goesWellWith = emptyList(),
                buyAgain = true,
                favourite = true,
                dateAdded = 0,
                catalogArticleNumber = null,
                addedBy = null,
                imageUrl = null,
            ),
            onClick = {},
        )
    }
}
