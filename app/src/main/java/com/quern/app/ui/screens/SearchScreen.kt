package com.quern.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quern.app.R
import com.quern.app.domain.model.MediaItem
import com.quern.app.ui.components.LocalTvRailInset
import com.quern.app.ui.components.LumaPosterCard
import com.quern.app.ui.theme.LocalQuernPalette
import com.quern.app.ui.theme.LumaTextPrimary
import com.quern.app.ui.theme.LumaTextSecondary
import com.quern.app.ui.theme.qString

@Composable
fun SearchScreen(
    query: String,
    results: List<MediaItem>,
    isSearching: Boolean,
    isTv: Boolean,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onItemClick: (MediaItem) -> Unit
) {
    val palette = LocalQuernPalette.current
    val railInset = if (isTv) LocalTvRailInset.current else 0.dp
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = if (isTv) railInset + 22.dp else 16.dp,
                end = if (isTv) 28.dp else 16.dp,
                top = 18.dp,
                bottom = 18.dp
            )
    ) {
        Text(
            text = qString(R.string.search_title),
            color = LumaTextPrimary,
            fontSize = if (isTv) 26.sp else 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = qString(R.string.search_subtitle),
            color = LumaTextSecondary,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(qString(R.string.search_placeholder), color = LumaTextSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = palette.focusRing) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = palette.glassElevated,
                    unfocusedContainerColor = palette.glassSurface,
                    focusedBorderColor = palette.focusRing,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onSearchSubmit,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(54.dp)
            ) {
                Text(qString(R.string.search_button), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(18.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isSearching -> {
                    CircularProgressIndicator(
                        color = palette.focusRing,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                results.isEmpty() && query.isNotBlank() -> {
                    Text(
                        text = qString(R.string.search_no_results, query),
                        color = LumaTextSecondary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                results.isEmpty() -> {
                    Text(
                        text = qString(R.string.search_prompt),
                        color = LumaTextSecondary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = if (isTv) 150.dp else 118.dp),
                        contentPadding = PaddingValues(bottom = 88.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        items(results, key = { "${it.origin}_${it.type}_${it.id}" }) { item ->
                            LumaPosterCard(
                                item = item,
                                isTv = isTv,
                                onClick = { onItemClick(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}
