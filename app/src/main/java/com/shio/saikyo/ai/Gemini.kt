package com.shio.saikyo.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.shio.saikyo.BuildConfig
import com.shio.saikyo.db.WordInfo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

//fun GenerateContentResponse.toJSON() = JSONTokener(text!!.replace("\n", "")).nextValue() as JSONObject
fun GenerateContentResponse.toJSON() = Json.parseToJsonElement(text ?: "{}").jsonObject

interface AIAssistant {
    suspend fun getLinguisticInfoOn(word: String, reading: String = word): WordInfo?
}

class Gemini(
    modelName: String,
    apiKey: String,
) : AIAssistant {
    private val model = GenerativeModel(
        modelName, apiKey, safetySettings = listOf(
            SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE),
            SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
        )
    )

    override suspend fun getLinguisticInfoOn(word: String, reading: String): WordInfo? {
        val prompt = """
            Output MUST be in JSON.
            The detailed_explanation should be as informative as possible.
            
            Example:
            {
            "word": "迷う",
            "reading": "まよう",
            "simple_explanation": "正しい道や方向がわからず、どうすればよいか判断できない状態。",
            "detailed_explanation": " 「迷う」は、いくつかの意味があります。\n方向や道筋が分からず、どうすればよいか判断できない。\n複数の選択肢があり、どれを選ぶべきか迷う。"
            "synonyms": ["惑う", "戸惑う", "逡巡する", "躊躇する", "迷走する"],
            "antonyms": ["決断する", "判断する", "確信する", "迷いない", "明快"],
            "examples": [
                "道に迷って、なかなか目的地に着か ない。",
                "将来の進路について、まだ迷っている。",
                "どの商品を選べばよいか、迷ってしまう。",
                "彼は、正しい判断に迷っていた。",
                "霧の中を彷徨い、方向感覚を失った。"
                ]
            }
            
            Question:
            {
                "word": "$word",
                "reading": "$reading",
                “simple_explanation":,
                “detailed_explanation":,
                "synonyms":,
                "antonyms":,
                "examples":
            }
        """.trimIndent()

        try {
            val res = model.generateContent(prompt)

            val json = res.toJSON()

            // TODO: kotlinx serializable for this
            return WordInfo(
                word = json["word"]!!.jsonPrimitive.content,
                reading = json["reading"]!!.jsonPrimitive.content,
                meaning = buildString {
                    append(json["simple_explanation"]!!.jsonPrimitive.content)
                    append("\n\n")
                    append(json["detailed_explanation"]!!.jsonPrimitive.content)
                },
                synonyms = json["synonyms"]!!.jsonArray.map { it.jsonPrimitive.content },
                antonyms = json["antonyms"]!!.jsonArray.map { it.jsonPrimitive.content },
                examples = json["examples"]!!.jsonArray.map { it.jsonPrimitive.content }
            )
        } catch (e: Exception) {
            // I guess this can happen due to a sudden cancellation?
            return null
        }
    }
}

fun connectToGemini(
    modelName: String = "gemini-pro",
    apiKey: String = BuildConfig.apiKey
) = Gemini(modelName, apiKey)
