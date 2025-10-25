package com.example.soillab.aggregatehub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.soillab.AppScreen
import com.example.soillab.R
import com.example.soillab.data.GsTestType
import com.example.soillab.data.SampleType
import com.example.soillab.ui.SectionTitle

data class AggregateTestAction(
    val titleResId: Int,
    val screen: AppScreen,
    val icon: ImageVector,
    val navigationArgs: Any? = null // Optional arguments to pass
)

object AggregateTestRepository {
    fun getAggregateTestActions(): List<AggregateTestAction> {
        return listOf(
            // Updated Sieve Analysis to navigate with AGGREGATE type
            AggregateTestAction(
                R.string.hub_sieve, // Reuse existing string
                AppScreen.SIEVE_ANALYSIS,
                Icons.Default.Grain, // Reuse existing icon
                navigationArgs = SampleType.AGGREGATE // Pass argument
            ),
            // Updated Specific Gravity to navigate with COARSE_AGG type
            AggregateTestAction(
                R.string.hub_specific_gravity, // Reuse existing string
                AppScreen.SPECIFIC_GRAVITY,
                Icons.Default.Iso, // Reuse existing icon
                navigationArgs = GsTestType.COARSE_AGG // Pass argument
            ),
            // Kept Aggregate Quality as is, it handles LA/Flakiness internally
            AggregateTestAction(
                R.string.hub_aggregate_quality, // Reuse existing string
                AppScreen.AGGREGATE_QUALITY,
                Icons.Default.Diamond // Reuse existing icon
            ),
        )
    }
}

@Composable
fun AggregateHubScreen(onNavigate: (AppScreen, Any?) -> Unit) { // Modified onNavigate to accept args
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        SectionTitle(title = stringResource(R.string.aggregate_tests_title)) // Title for the section
        LazyVerticalGrid(
            columns = GridCells.Fixed(3), // Adjust number of columns if needed
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(AggregateTestRepository.getAggregateTestActions()) { action ->
                AggregateTestCard(action = action, onNavigate = onNavigate)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AggregateTestCard(action: AggregateTestAction, onNavigate: (AppScreen, Any?) -> Unit) { // Modified onNavigate
    Card(
        onClick = { onNavigate(action.screen, action.navigationArgs) }, // Pass args on click
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f) // Keep cards square
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = stringResource(id = action.titleResId),
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(id = action.titleResId),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

