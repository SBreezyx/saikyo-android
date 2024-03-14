package com.shio.saikyo.parser

import com.google.ai.client.generativeai.GenerativeModel
import com.shio.saikyo.i18n.LangCode

private val API_KEY = "AIzaSyBQdZmxy9J98sboJAQxdn_fvmp5BekeSA8"

suspend fun parseJp(gemini: GenerativeModel, text: String): List<Pair<Int, Int>> {
    val prompt = arrayOf(
        "以下のように、分ち書きしてください。",
                "JSONだけを挙げてください",
                "例文: \"どんな時だって万が一生きてきても忘れないでくれ\"",
                "正解: ```",
                "{",
                "\"words\": [\"どんな\", \"時\", \"だって\", \"万が一\", \"生きてきて\", \"も\", \"忘れないで\", \"くれ\"],",
                "\"offsets\": [[0, 3], [3, 4], [4, 7], [7, 10], [10, 15], [15, 16], [16, 21], [21, 23]]",
                "}",
                "```",
                "例文: \"事務所\\nが、とおい　よな\"",
                "正解: ```",
                "{",
                "\"words\": [\"事務所\", \"\\n\", \"が\", \"、\", \"とおい\", \"　\", \"よ\", \"な\"],",
                "\"offsets\": [[0, 3], [3, 4], [4, 5], [5, 6], [6, 9], [9, 10], [10, 11], [11, 12]]",
                "}",
                "```",
                "例文: \"10枚で百キロ何て12345xやばい\"",
                "正解: ```",
                "{",
                "\"words\": [\"10枚\", \"で\", \"百キロ\", \"何て\", \"12345\", \"x\", \"やばい\"]",
                "\"offsets\": [[0, 3], [3, 4], [4, 7], [7, 9], [9, 14], [14, 15], [15, 18]]",
                "},",
                "```",
                "文: \"${text}\"", // this will probably break if the text contains quote characters
                "正解: "
    ).joinToString("\n")

//    val geminiRes = gemini.generateContent(prompt)
//    geminiRes.text

    return mutableListOf()
}

class GeminiParser(apiKey: String = API_KEY) : IParser {
    private val model: GenerativeModel

    init {
        model = GenerativeModel("gemini-pro", apiKey)
    }

    // TODO: when the "targetted lang" app setting changes, use a specific instance
    // fucking java
    override suspend fun parse(lang: LangCode, text: String): List<Pair<Int, Int>> {
        return when (lang) {
            LangCode.ja -> {
                parseJp(model, text)
            }

            else -> {
                mutableListOf()
            }
        }
    }

}