package com.simplyroutine.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.simplyroutine.R

val LexendFontFamily = FontFamily(
    Font(R.font.lexend_variablefont_wght, weight = FontWeight.Normal),
    Font(R.font.lexend_variablefont_wght, weight = FontWeight.Medium),
    Font(R.font.lexend_variablefont_wght, weight = FontWeight.SemiBold),
    Font(R.font.lexend_variablefont_wght, weight = FontWeight.Bold),
)

val Typography = Typography(
    displayLarge   = TextStyle(fontFamily = LexendFontFamily, fontWeight = FontWeight.Normal,   fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium  = TextStyle(fontFamily = LexendFontFamily, fontWeight = FontWeight.Normal,   fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall   = TextStyle(fontFamily = LexendFontFamily, fontWeight = FontWeight.Normal,   fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge  = TextStyle(fontFamily = LexendFontFamily, fontWeight = FontWeight.Normal,   fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = LexendFontFamily, fontWeight = FontWeight.Normal,   fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall  = TextStyle(fontFamily = LexendFontFamily, fontWeight = FontWeight.Normal,   fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge     = TextStyle(fontFamily = LexendFontFamily, fontWeight = FontWeight.Normal,   fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium    = TextStyle(fontFamily = LexendFontFamily, fontWeight = FontWeight.Medium,   fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall     = TextStyle(fontFamily = LexendFontFamily, fontWeight = FontWeight.Medium,   fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge      = TextStyle(fontFamily = LexendFontFamily, fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium     = TextStyle(fontFamily = LexendFontFamily, fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall      = TextStyle(fontFamily = LexendFontFamily, fontWeight = FontWeight.Normal,   fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge     = TextStyle(fontFamily = LexendFontFamily, fontWeight = FontWeight.Medium,   fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium    = TextStyle(fontFamily = LexendFontFamily, fontWeight = FontWeight.Medium,   fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall     = TextStyle(fontFamily = LexendFontFamily, fontWeight = FontWeight.Medium,   fontSize = 11.sp, lineHeight = 16.sp),
)
