package com.example.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GeoPrimary

/**
 * Modifier extension to enable seamless TV D-Pad (Arrow keys) and Keyboard TAB/ENTER navigation,
 * with visible active focus rings for TV Box and hardware keyboard users.
 */
@Composable
fun Modifier.dpadAndTabNav(
    focusManager: FocusManager,
    onEnter: (() -> Unit)? = null,
    borderShape: Shape = RoundedCornerShape(12.dp),
    focusedBorderWidth: Dp = 2.dp,
    focusedBorderColor: Color = GeoPrimary
): Modifier {
    var isFocused by remember { mutableStateOf(false) }

    return this
        .onFocusChanged { isFocused = it.isFocused }
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown) {
                when (event.key) {
                    Key.Tab -> {
                        if (event.isShiftPressed) {
                            focusManager.moveFocus(FocusDirection.Previous)
                        } else {
                            focusManager.moveFocus(FocusDirection.Next)
                        }
                        true
                    }
                    Key.DirectionDown -> {
                        focusManager.moveFocus(FocusDirection.Down)
                        true
                    }
                    Key.DirectionUp -> {
                        focusManager.moveFocus(FocusDirection.Up)
                        true
                    }
                    Key.DirectionLeft -> {
                        focusManager.moveFocus(FocusDirection.Left)
                        true
                    }
                    Key.DirectionRight -> {
                        focusManager.moveFocus(FocusDirection.Right)
                        true
                    }
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        if (onEnter != null) {
                            onEnter()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            } else {
                false
            }
        }
        .focusable()
        .then(
            if (isFocused) {
                Modifier.border(focusedBorderWidth, focusedBorderColor, borderShape)
            } else {
                Modifier
            }
        )
}

/**
 * Helper for TextFields to handle TAB and UP/DOWN key navigation cleanly.
 */
@Composable
fun Modifier.textFieldKeyNavigation(
    focusManager: FocusManager,
    onDone: (() -> Unit)? = null
): Modifier {
    return this.onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown) {
            when (event.key) {
                Key.Tab -> {
                    if (event.isShiftPressed) {
                        focusManager.moveFocus(FocusDirection.Up)
                    } else {
                        focusManager.moveFocus(FocusDirection.Down)
                    }
                    true
                }
                Key.DirectionDown -> {
                    focusManager.moveFocus(FocusDirection.Down)
                    true
                }
                Key.DirectionUp -> {
                    focusManager.moveFocus(FocusDirection.Up)
                    true
                }
                Key.Enter, Key.NumPadEnter -> {
                    if (onDone != null) {
                        onDone()
                        true
                    } else {
                        focusManager.moveFocus(FocusDirection.Down)
                        true
                    }
                }
                else -> false
            }
        } else {
            false
        }
    }
}
