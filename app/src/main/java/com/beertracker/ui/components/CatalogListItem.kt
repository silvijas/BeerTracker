package com.beertracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.beertracker.domain.CatalogProduct
import com.beertracker.ui.theme.BeerTrackerSpacing

/** One browsable row: a catalog product, plus the user's beer when logged. */
data class CatalogRow(
    val product: CatalogProduct,
    val triedBeerId: String?,
    val grade: Int?,
    val tried: Boolean,
)

/**
 * A catalog product row shared by the catalog browser and the brewery
 * beers screen. [subtitle] is caller-supplied so each screen can decide
 * whether to repeat the brewery name. `BeerThumbnail` and `GradeMark` need
 * no import: both already live in this package.
 */
@Composable
internal fun CatalogListItem(
    row: CatalogRow,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val product = row.product
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BeerThumbnail(model = product.displayImageUrl)
        Column(Modifier.weight(1f)) {
            Text(
                text = product.name,
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
            val meta = catalogItemMeta(product)
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (row.triedBeerId != null) {
            GradeMark(grade = row.grade, tried = row.tried, size = 40.dp)
        }
    }
}

internal fun catalogItemMeta(product: CatalogProduct): String = listOfNotNull(
    product.price?.let { "$it kr" },
    product.volumeMl?.let { "$it ml" },
    product.alcoholPercent?.let { "$it %" },
).joinToString(", ")
