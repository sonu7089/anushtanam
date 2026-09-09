package com.mantrasiddhi.presentation.screens.settings

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.data.preferences.SandhyaSettings
import com.mantrasiddhi.presentation.components.LotusCard
import com.mantrasiddhi.resources.R

@Composable
internal fun SandhyaLocationSettings(settings: SandhyaSettings, locationViewModel: SandhyaLocationViewModel) {
    val context = LocalContext.current
    var showRegionDialog by rememberSaveable { mutableStateOf(false) }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locationViewModel.useMyLocation()
        else Toast.makeText(context, R.string.sandhya_location_denied, Toast.LENGTH_LONG).show()
    }
    LaunchedEffect(locationViewModel) {
        locationViewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    RegionCard(
        regionName = settings.region.displayName.ifBlank {
            stringResource(R.string.sandhya_region_current)
        },
        onChangeClick = { showRegionDialog = true },
        onUseLocationClick = {
            if (locationViewModel.locationPermissionGranted()) {
                locationViewModel.useMyLocation()
            } else {
                locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
    )

    if (showRegionDialog) {
        val searchState by locationViewModel.searchState.collectAsState()
        RegionPickerDialog(
            searchState = searchState,
            onSearch = locationViewModel::searchLocations,
            onSelectPlace = { place ->
                locationViewModel.selectSearchedPlace(place)
                showRegionDialog = false
            },
            onDismiss = {
                locationViewModel.clearLocationSearch()
                showRegionDialog = false
            }
        )
    }
}

@Composable
internal fun RegionCard(
    regionName: String,
    onChangeClick: () -> Unit,
    onUseLocationClick: () -> Unit
) {
    LotusCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.sandhya_region_label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = regionName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onChangeClick, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.sandhya_change))
                }
                FilledTonalButton(onClick = onUseLocationClick, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.sandhya_use_location))
                }
            }
        }
    }
}

/**
 * Free-text location search dialog: the user types any town/place, the platform
 * geocoder resolves candidates, and tapping one sets it as the Sandhya region.
 * Replaces the earlier fixed city list.
 */
@Composable
private fun RegionPickerDialog(
    searchState: LocationSearchState,
    onSearch: (String) -> Unit,
    onSelectPlace: (com.mantrasiddhi.data.location.GeocodingService.GeocodedPlace) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sandhya_select_region_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.sandhya_search_hint)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { onSearch(query) }
                    ),
                    trailingIcon = {
                        androidx.compose.material3.IconButton(onClick = { onSearch(query) }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.sandhya_search_action)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (searchState) {
                        is LocationSearchState.Idle -> {
                            Text(
                                text = stringResource(R.string.sandhya_search_prompt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                        is LocationSearchState.Searching -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.sandhya_searching),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        is LocationSearchState.NoResults -> {
                            Text(
                                text = stringResource(R.string.sandhya_search_no_results),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                        is LocationSearchState.Error -> {
                            Text(
                                text = stringResource(R.string.sandhya_search_error),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                        is LocationSearchState.Results -> {
                            searchState.places.forEach { place ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable { onSelectPlace(place) }
                                        .padding(vertical = 12.dp, horizontal = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = place.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.target_dialog_cancel)) }
        }
    )
}
