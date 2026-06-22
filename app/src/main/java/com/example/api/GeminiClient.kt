package com.example.api

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiClient {
    private const val TAG = "GeminiClient"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val candidateModels = listOf(
        "gemini-2.5-flash",
        "gemini-flash-latest",
        "gemini-3.1-pro-preview",
        "gemini-3.5-flash"
    )

    suspend fun queryGemini(userQuery: String, keyOverride: String = ""): String = withContext(Dispatchers.IO) {
        val apiKey = keyOverride.ifEmpty { BuildConfig.GEMINI_API_KEY }
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Chưa cấu hình API Key cho Gemini AI. Hãy nhập API Key trực tiếp trong phần Cài đặt của trợ lý AI trên ứng dụng."
        }

        val systemPrompt = "Bạn là trợ lý thông báo báo thức thông minh cho ca làm việc. Hãy đóng vai trò trợ lý tóm tắt hoặc chuẩn bị thông tin theo yêu cầu của người dùng bên dưới. Hãy trả lời bằng tiếng Việt cực kỳ ngắn gọn, cô đọng (khoảng 3-5 dòng, tối đa 80-120 từ), trực quan, có chứa emoji thích hợp để người dùng đọc lướt qua nhanh chóng ngay khi thức dậy trên thông báo điện thoại. " +
                "Hãy tự động cập nhật hoặc suy luận thông tin nếu cần, trả lời một cách tự tin, hữu ích nhất."

        var lastErrorMsg = ""
        var lastErrorCode = 0

        for (model in candidateModels) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            Log.d(TAG, "Attempting to query Gemini using model: $model")
            try {
                val jsonBody = JSONObject().apply {
                    val contentsArray = JSONArray().apply {
                        val contentObj = JSONObject().apply {
                            val partsArray = JSONArray().apply {
                                val partObj = JSONObject().apply {
                                    put("text", "$systemPrompt\n\nYêu cầu của người dùng: $userQuery")
                                }
                                put(partObj)
                            }
                            put("parts", partsArray)
                        }
                        put(contentObj)
                    }
                    put("contents", contentsArray)
                }

                val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBodyStr = response.body?.string() ?: ""
                        Log.d(TAG, "Successfully query Gemini with model $model. Response size: ${responseBodyStr.length}")
                        
                        val root = JSONObject(responseBodyStr)
                        val candidates = root.getJSONArray("candidates")
                        val firstCandidate = candidates.getJSONObject(0)
                        val content = firstCandidate.getJSONObject("content")
                        val parts = content.getJSONArray("parts")
                        val firstPart = parts.getJSONObject(0)
                        val text = firstPart.getString("text")

                        return@withContext text.trim()
                    } else {
                        val errorStr = response.body?.string() ?: ""
                        lastErrorCode = response.code
                        lastErrorMsg = "Model $model failed with code ${response.code}: $errorStr"
                        Log.e(TAG, lastErrorMsg)
                    }
                }
            } catch (e: Exception) {
                lastErrorMsg = "Model $model threw exception: ${e.localizedMessage}"
                Log.e(TAG, lastErrorMsg, e)
            }
        }

        // If we reached here, all models in the list failed
        return@withContext if (lastErrorCode != 0) {
            "Lỗi tải thông tin AI (Mã lỗi: $lastErrorCode)."
        } else {
            "Không thể kết nối AI (Lỗi kết nối mạng: $lastErrorMsg)."
        }
    }
}
