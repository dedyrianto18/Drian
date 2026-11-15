package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast

/**
 * Helper for connecting directly to the Google Gemini Android app
 * installed on the user's phone (`com.google.android.apps.bard`),
 * with safe fallbacks to Gemini Web / Google Play Store.
 */
object GeminiAppBridge {

    const val GEMINI_PACKAGE_NAME = "com.google.android.apps.bard"
    private const val GEMINI_WEB_URL = "https://gemini.google.com/app"

    fun isGeminiAppInstalled(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(GEMINI_PACKAGE_NAME, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(GEMINI_PACKAGE_NAME, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openGeminiApp(context: Context): Boolean {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(GEMINI_PACKAGE_NAME)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            return true
        }

        val assistIntent = Intent(Intent.ACTION_ASSIST).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (assistIntent.resolveActivity(pm) != null) {
            context.startActivity(assistIntent)
            return true
        }

        openGeminiWeb(context)
        return false
    }

    fun sendPromptToGeminiApp(context: Context, prompt: String): Boolean {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isNotEmpty()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Prompt Gemini", cleanPrompt))
        }

        val pm = context.packageManager
        if (isGeminiAppInstalled(context)) {
            if (cleanPrompt.isNotEmpty()) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    setPackage(GEMINI_PACKAGE_NAME)
                    putExtra(Intent.EXTRA_TEXT, cleanPrompt)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (shareIntent.resolveActivity(pm) != null) {
                    context.startActivity(shareIntent)
                    Toast.makeText(context, "Mengirim prompt ke aplikasi Gemini...", Toast.LENGTH_SHORT).show()
                    return true
                }
            }

            val launchIntent = pm.getLaunchIntentForPackage(GEMINI_PACKAGE_NAME)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                if (cleanPrompt.isNotEmpty()) {
                    Toast.makeText(
                        context,
                        "Aplikasi Gemini dibuka & prompt disalin ke clipboard (tinggal tempel).",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return true
            }
        }

        openGeminiWeb(context)
        Toast.makeText(
            context,
            "Aplikasi Gemini tidak ditemukan, membuka Gemini Web & prompt disalin.",
            Toast.LENGTH_LONG
        ).show()
        return false
    }

    fun openGeminiWeb(context: Context) {
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(GEMINI_WEB_URL)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(webIntent) }
    }

    fun openGeminiPlayStore(context: Context) {
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$GEMINI_PACKAGE_NAME")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(marketIntent)
        }.onFailure {
            val webPlay = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$GEMINI_PACKAGE_NAME")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(webPlay) }
        }
    }
}
