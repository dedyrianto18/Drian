package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "CryptoAiService"

data class AiAnalysisResult(
    val mainAnalysis: String,
    val thinkingProcess: String? = null,
    val tradeActionRecommendation: String? = null,
    val modelName: String = "gemini-4-argon"
)

class CryptoAiService {

    // 1. High-Thinking Model: gemini-4-argon with thinkingLevel: HIGH (no maxOutputTokens)
    private val argonThinkingModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-4-argon",
            systemInstruction = content {
                text(
                    "You are CryptoQuant AI, a Senior Algorithmic Trader and Risk Manager. " +
                    "Conduct institutional-grade technical analysis focusing on Grid Trading corridors and Volatility Breakout momentum. " +
                    "Always structure your output with: 1) Executive Market Summary, 2) Key Support/Resistance Rungs, " +
                    "3) Indicator Confluence (RSI, Bollinger Squeeze, MACD), 4) Automated Bot Recommendations (Grid Bounds or Breakout Trigger), and 5) Risk Guardrails."
                )
            },
            generationConfig = generationConfig {
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.HIGH
                }
            }
        )
    }

    // 2. Audio Transcription Model: gemini-3.5-transcribe
    private val transcribeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-transcribe",
            systemInstruction = content {
                text("You are an accurate voice transcription engine. Transcribe the user's speech clearly in its original language (Indonesian or English).")
            }
        )
    }

    // 3. Vision & Video Understanding Model: gemini-4-argon
    private val multimodalArgonModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-4-argon",
            systemInstruction = content {
                text(
                    "You are Gemini, a helpful multimodal AI assistant. Analyze uploaded photos, documents, screenshots, " +
                    "or videos accurately and explain key findings clearly in Indonesian or the user's language."
                )
            }
        )
    }

    // 4. General Gemini Chat Models (General Q&A, Coding, Writing, Daily Tasks, or Trading if asked)
    private val generalArgonModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-4-argon",
            systemInstruction = content {
                text(
                    "Kamu adalah asisten AI Gemini umum yang cerdas, ramah, dan serbaguna. " +
                    "Jawab pertanyaan apa saja mulai dari pengetahuan umum, coding, penulisan, bisnis, terjemahan, matematika, " +
                    "hingga analisis pasar Exness/MT5 jika pengguna menanyakannya. Gunakan bahasa Indonesia yang jelas kecuali diminta bahasa lain."
                )
            },
            generationConfig = generationConfig {
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.HIGH
                }
            }
        )
    }

    private val flashModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "Kamu adalah asisten Gemini umum yang cepat, ramah, dan membantu untuk topik apa pun " +
                    "(pertanyaan harian, coding, meringkas teks, ide konten, maupun trading jika diminta)."
                )
            }
        )
    }

    private val flashLiteModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            systemInstruction = content {
                text("Kamu adalah Gemini Flash-Lite. Berikan jawaban singkat, padat, dan akurat untuk pertanyaan apa saja.")
            }
        )
    }

    /**
     * Conduct deep technical analysis with high thinking level
     */
    suspend fun analyzeMarketWithHighThinking(
        pairSymbol: String,
        currentPrice: Double,
        rsi: Double,
        bbUpper: Double,
        bbMiddle: Double,
        bbLower: Double,
        macd: Double,
        learnedRules: List<String>
    ): Result<AiAnalysisResult> {
        return try {
            val rulesText = if (learnedRules.isNotEmpty()) {
                "\nUser-Taught Custom Strategy Directives:\n" + learnedRules.joinToString("\n") { "- $it" }
            } else ""

            val prompt = """
                Analyze current market conditions for $pairSymbol:
                - Current Price: $currentPrice USDT
                - RSI (14): ${"%.2f".format(rsi)}
                - Bollinger Upper: ${"%.2f".format(bbUpper)}
                - Bollinger Middle: ${"%.2f".format(bbMiddle)}
                - Bollinger Lower: ${"%.2f".format(bbLower)}
                - MACD: ${"%.4f".format(macd)}
                $rulesText
                
                Evaluate whether conditions favor Grid Trading or Volatility Breakout. Recommend optimal grid bounds or breakout trigger levels.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                argonThinkingModel.generateContent(prompt)
            }

            val text = response.text ?: "Analisis pasar selesai."
            val thought = response.thoughtSummary

            Result.success(
                AiAnalysisResult(
                    mainAnalysis = text,
                    thinkingProcess = thought,
                    modelName = "gemini-4-argon"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "High thinking analysis failed", e)
            Result.failure(e)
        }
    }

    /**
     * Transcribe spoken audio using gemini-3.5-transcribe
     */
    suspend fun transcribeAudio(audioBytes: ByteArray, mimeType: String = "audio/wav"): Result<String> {
        return try {
            val response = withTimeout(45_000L) {
                transcribeModel.generateContent(
                    content {
                        inlineData(audioBytes, mimeType)
                        text("Transcribe this audio recording of a trading voice command. Return only the transcription.")
                    }
                )
            }
            val text = response.text?.trim() ?: ""
            Result.success(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            Result.failure(e)
        }
    }

    /**
     * Analyze uploaded chart image using gemini-4-argon
     */
    suspend fun analyzeChartImage(
        bitmap: Bitmap,
        userPrompt: String = "Analyze this trading chart. Identify key support/resistance lines, trend, and recommended bot setup."
    ): Result<AiAnalysisResult> {
        return try {
            val response = withTimeout(60_000L) {
                multimodalArgonModel.generateContent(
                    content {
                        image(bitmap)
                        text(userPrompt)
                    }
                )
            }
            Result.success(
                AiAnalysisResult(
                    mainAnalysis = response.text ?: "Analisis gambar selesai.",
                    thinkingProcess = response.thoughtSummary,
                    modelName = "gemini-4-argon"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Chart image analysis failed", e)
            Result.failure(e)
        }
    }

    /**
     * Analyze video content using gemini-4-argon
     */
    suspend fun analyzeVideo(
        videoBytes: ByteArray,
        mimeType: String = "video/mp4",
        userPrompt: String = "Analyze this trading video. Extract the core market breakdown, price target levels, and volatility triggers mentioned."
    ): Result<AiAnalysisResult> {
        return try {
            val response = withTimeout(60_000L) {
                multimodalArgonModel.generateContent(
                    content {
                        inlineData(videoBytes, mimeType)
                        text(userPrompt)
                    }
                )
            }
            Result.success(
                AiAnalysisResult(
                    mainAnalysis = response.text ?: "Analisis video selesai.",
                    thinkingProcess = response.thoughtSummary,
                    modelName = "gemini-4-argon"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Video analysis failed", e)
            Result.failure(e)
        }
    }

    /**
     * Generate technical infographic or strategy card with aspect ratio control
     */
    suspend fun generateStrategyInfographic(
        prompt: String,
        aspectRatio: AspectRatio = AspectRatio.LANDSCAPE_16x9
    ): Result<Bitmap> {
        return try {
            val imageGenModel = Firebase.ai.generativeModel(
                modelName = "gemini-nano-banana-2.1",
                generationConfig = generationConfig {
                    responseModalities = listOf(ResponseModality.IMAGE)
                    imageConfig = ImageConfig.builder()
                        .setAspectRatio(aspectRatio)
                        .setImageSize(ImageSize.SIZE_1K)
                        .build()
                }
            )

            val fullPrompt = "High quality modern visual artwork or infographic illustrating: $prompt, crisp composition, clear visual details"

            val response = withTimeout(60_000L) {
                imageGenModel.generateContent(fullPrompt)
            }

            val imagePart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()

            val bitmap = imagePart?.image
            if (bitmap != null) {
                Result.success(bitmap)
            } else {
                Result.failure(IllegalStateException("Model did not return an image"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image generation failed", e)
            Result.failure(e)
        }
    }

    /**
     * Multi-turn general Gemini chat message sending with model selection
     */
    suspend fun sendChatMessage(
        message: String,
        modelType: String = "gemini-3.8-flash",
        enableThinking: Boolean = true
    ): Result<AiAnalysisResult> {
        return try {
            val selectedModel = when (modelType) {
                "gemini-4-argon" -> if (enableThinking) generalArgonModel else multimodalArgonModel
                "gemini-3.5-flash-lite", "gemini-3.1-flash-lite" -> flashLiteModel
                else -> flashModel
            }

            val response = withTimeout(60_000L) {
                selectedModel.generateContent(message)
            }

            Result.success(
                AiAnalysisResult(
                    mainAnalysis = response.text ?: "",
                    thinkingProcess = response.thoughtSummary,
                    modelName = modelType
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Chat message generation failed", e)
            Result.failure(e)
        }
    }
}
