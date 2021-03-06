package com.domburg.newnote.utils

import android.widget.Toast
import androidx.activity.ComponentActivity

/**
 * Convience method for toasts
 */

inline fun ComponentActivity.toast(message: CharSequence) =
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()