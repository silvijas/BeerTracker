package com.beertracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.beertracker.domain.Pairing
import com.beertracker.ui.theme.BeerTrackerSpacing

/**
 * The pairings of one beer, as an icon above its label, the way
 * systembolaget.se presents them. Values in the known vocabulary come first,
 * in vocabulary order; anything the user typed themselves follows, with its
 * label alone and no icon.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PairingRow(
    pairings: List<String>,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    if (pairings.isEmpty()) {
        Text(
            text = emptyText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    val known = Pairing.entries.filter { entry -> pairings.any { it == entry.label } }
    val custom = pairings.filter { Pairing.fromLabel(it) == null }.distinct()
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.medium),
    ) {
        known.forEach { PairingCell(label = pairingLabel(it), pairing = it) }
        custom.forEach { PairingCell(label = it, pairing = null) }
    }
}

@Composable
private fun PairingCell(label: String, pairing: Pairing?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.xSmall),
        modifier = Modifier.width(72.dp),
    ) {
        if (pairing != null) {
            PairingIcon(pairing = pairing, size = 32.dp)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
