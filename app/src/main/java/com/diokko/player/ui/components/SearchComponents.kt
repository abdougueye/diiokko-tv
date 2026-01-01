package com.diokko.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diokko.player.ui.theme.*

/**
 * TV-optimized search bar component
 */
@Composable
fun TvSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit = {},
    onClear: () -> Unit = {},
    onNavigateDown: () -> Unit = {},
    placeholder: String = "Search...",
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    enabled: Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(DiokkoShapes.medium)
            .background(
                if (isFocused) DiokkoColors.SurfaceElevated 
                else DiokkoColors.Surface
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) DiokkoColors.Accent else DiokkoColors.SurfaceLight,
                shape = DiokkoShapes.medium
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Search icon
            Text(
                text = "🔍",
                fontSize = 18.sp,
                modifier = Modifier.padding(end = 12.dp)
            )
            
            Box(modifier = Modifier.weight(1f)) {
                // Placeholder
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = DiokkoTypography.bodyMedium,
                        color = DiokkoColors.TextSecondary
                    )
                }
                
                // Text field
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = DiokkoColors.TextPrimary,
                        fontSize = 16.sp
                    ),
                    cursorBrush = SolidColor(DiokkoColors.Accent),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { onSearch() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { isFocused = it.isFocused }
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.Escape, Key.Back -> {
                                        if (query.isNotEmpty()) {
                                            onClear()
                                            true
                                        } else false
                                    }
                                    Key.Enter, Key.DirectionCenter -> {
                                        onSearch()
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        onNavigateDown()
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                )
            }
            
            // Clear button (when there's text)
            if (query.isNotEmpty()) {
                Text(
                    text = "✕",
                    fontSize = 16.sp,
                    color = DiokkoColors.TextSecondary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

/**
 * Compact search bar for contextual search in screens
 */
@Composable
fun ContextualSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit = {},
    onNavigateDown: () -> Unit = {},
    categoryName: String? = null,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(DiokkoShapes.small)
            .background(
                if (isFocused) DiokkoColors.SurfaceElevated 
                else DiokkoColors.Surface
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) DiokkoColors.Accent else DiokkoColors.SurfaceLight,
                shape = DiokkoShapes.small
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Search icon
        Text(
            text = "🔍",
            fontSize = 14.sp,
            modifier = Modifier.padding(end = 8.dp)
        )
        
        Box(modifier = Modifier.weight(1f)) {
            // Placeholder - now searches all content
            if (query.isEmpty()) {
                Text(
                    text = "Search all...",
                    style = DiokkoTypography.bodySmall,
                    color = DiokkoColors.TextSecondary
                )
            }
            
            // Text field
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = DiokkoColors.TextPrimary,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(DiokkoColors.Accent),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { isFocused = it.isFocused }
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.Escape -> {
                                    if (query.isNotEmpty()) {
                                        onClear()
                                        true
                                    } else false
                                }
                                Key.DirectionDown -> {
                                    onNavigateDown()
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            )
        }
        
        // Clear button
        if (query.isNotEmpty()) {
            Text(
                text = "✕",
                fontSize = 14.sp,
                color = DiokkoColors.TextSecondary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * Search result section header
 */
@Composable
fun SearchResultHeader(
    title: String,
    count: Int,
    emoji: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = emoji,
            fontSize = 20.sp,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = title,
            style = DiokkoTypography.titleMedium,
            color = DiokkoColors.TextPrimary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "($count)",
            style = DiokkoTypography.bodySmall,
            color = DiokkoColors.TextSecondary
        )
    }
}

/**
 * Empty search results message
 */
@Composable
fun EmptySearchResults(
    query: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🔍",
            fontSize = 48.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No results found",
            style = DiokkoTypography.titleMedium,
            color = DiokkoColors.TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "No matches for \"$query\"",
            style = DiokkoTypography.bodyMedium,
            color = DiokkoColors.TextSecondary
        )
    }
}
