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
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    suspend fun queryGemini(userQuery: String, keyOverride: String = ""): String = withContext(Dispatchers.IO) {
        val apiKey = keyOverride.ifEmpty { BuildConfig.GEMINI_API_KEY }
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Chưa cấu hình API Key cho Gemini AI. Hãy nhập API Key trực tiếp trong phần Cài đặt của trợ lý AI trên ứng dụng."
        }

        val systemPrompt = "Bạn là trợ lý thông báo báo thức thông minh cho ca làm việc. Hãy đóng vai trò trợ lý tóm tắt hoặc chuẩn bị thông tin theo yêu cầu của người dùng bên dưới. Hãy trả lời bằng tiếng Việt cực kỳ ngắn gọn, cô đọng (khoảng 3-5 dòng, tối đa 80-120 từ), trực quan, có chứa emoji thích hợp để người dùng đọc lướt qua nhanh chóng ngay khi thức dậy trên thông báo điện thoại. " +
                "Hãy tự động cập nhật hoặc suy luận thông tin nếu cần, trả lời một cách tự tin, hữu ích nhất."

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
                .url("$BASE_URL?key=$apiKey")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorStr = response.body?.string() ?: ""
                    Log.e(TAG, "Request failed: Code=${response.code}, Msg=$errorStr")
                    return@withContext "Lỗi tải thông tin AI (Mã lỗi: ${response.code})."
                }

                val responseBodyStr = response.body?.string() ?: ""
                Log.d(TAG, "Response size: ${responseBodyStr.length}")
                
                val root = JSONObject(responseBodyStr)
                val candidates = root.getJSONArray("candidates")
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.getJSONObject("content")
                val parts = content.getJSONArray("parts")
                val firstPart = parts.getJSONObject(0)
                val text = firstPart.getString("text")

                return@withContext text.trim()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during queryGemini: ${e.message}", e)
            return@withContext "Không thể kết nối AI (Lỗi kết nối mạng: ${e.localizedMessage})."
        }
    }
}
