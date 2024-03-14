package com.shio.saikyo.parser

import com.shio.saikyo.i18n.LangCode

interface IParser {
    suspend fun parse(lang: LangCode, text: String): List<Pair<Int, Int>>
}