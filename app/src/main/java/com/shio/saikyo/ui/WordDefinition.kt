package com.shio.saikyo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shio.saikyo.SaikyoApp
import com.shio.saikyo.R
import com.shio.saikyo.db.WordDAO
import com.shio.saikyo.db.WordInfo
import com.shio.saikyo.ui.primitives.DefaultAppBar
import com.shio.saikyo.ui.primitives.MenuItemTuple
import com.shio.saikyo.ui.primitives.NavBackButton
import com.shio.saikyo.ui.text.Text
import com.shio.saikyo.ui.theme.SaikyoTheme
import com.shio.saikyo.util.verticalScrollbar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch


class WordDefVM(
    lemmaId: Int,
    val wi: WordDAO,
) : ViewModel() {
    val wordInfo: MutableStateFlow<WordInfo?> = MutableStateFlow(null)

    init {
        viewModelScope.launch {
            val lemma = wi.getAllInfo(lemmaId)

            wordInfo.value = lemma
        }
    }

    companion object {
        fun Factory(lemmaId: Int) = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SaikyoApp)
                WordDefVM(lemmaId, app.db.words())
            }
        }
    }
}

fun WordDefVM.clearMeaningFor(lemmaId: Int) {
    viewModelScope.launch {
        wi.updateLemma(lemmaId)
    }
}

@Composable
fun WaitForWordInfo(modifier: Modifier = Modifier) = Box(
    contentAlignment = Alignment.Center,
    modifier = modifier.fillMaxSize()
) {
    CircularProgressIndicator()
}

@Composable
fun WordDefinition(
    wi: WordInfo,
    modifier: Modifier = Modifier
) = Column(
    modifier = modifier
        .fillMaxSize()
        .padding(end = 4.dp)
        .verticalScrollbar(rememberScrollState())
        .padding(start = 24.dp, end = 20.dp, bottom = 8.dp)
) {
    val sectionHeading = MaterialTheme.typography.labelLarge.copy(
        color = MaterialTheme.colorScheme.secondary,
        fontWeight = FontWeight.Bold
    )
    val newlineSpace = Modifier.height(24.dp)
    val ctxMenu = ContextMenu.from(
        ContextMenuItem.Copy,
        ContextMenuItem.Search(LocalContext.current),
        ContextMenuItem.SelectAll,
        *LocalTextProcessors.current,
        maxVisible = 2
    )

    Text(
        text = wi.word,
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.primary,
    )

    Spacer(newlineSpace)

    Text(stringResource(R.string.word_def_reading), style = sectionHeading)
    Text(wi.reading, contextMenu = ctxMenu)

    Spacer(newlineSpace)

    Text(stringResource(R.string.word_def_meaning), style = sectionHeading)
    Text(wi.meaning, contextMenu = ctxMenu)

    Spacer(newlineSpace)

    Text(stringResource(R.string.word_def_synonym), style = sectionHeading)
    for (syn in wi.synonyms) {
        Text("• $syn", contextMenu = ctxMenu)
    }

    Spacer(newlineSpace)

    Text(stringResource(R.string.word_def_antonym), style = sectionHeading)
    for (ant in wi.antonyms) {
        Text("• $ant", contextMenu = ctxMenu)
    }

    Spacer(newlineSpace)

    Text(stringResource(R.string.word_def_examples), style = sectionHeading)
    for ((ix, ex) in wi.examples.withIndex()) {
        Text("${ix + 1}. $ex", contextMenu = ctxMenu)
    }
}

@Composable
fun DefinitionScreen(
    lemmaId: Int,
    navToSettings: () -> Unit,
    navBack: () -> Unit,
    // TODO: consider if WordDefinition VM creation can be moved elsewhere
    vm: WordDefVM = viewModel(factory = WordDefVM.Factory(lemmaId))
) = Scaffold(topBar = {
    DefaultAppBar(
        navigation = { NavBackButton(navBack) },
        menuItems = listOf(
            MenuItemTuple(
                { Icon(imageVector = Icons.Default.Delete, contentDescription = null) },
                { Text("Delete", color = Color.Red) },
                {
                    vm.clearMeaningFor(lemmaId)
                    navBack()
                }
            ),
            MenuItemTuple(
                { Icon(imageVector = Icons.Default.Settings, contentDescription = null) },
                { Text("Settings") },
                navToSettings
            )
        )
    )
}) { padding ->
    val contentPadding = Modifier.padding(padding)
    val wordInfo = vm.wordInfo.collectAsState(initial = null)

    when (wordInfo.value) {
        null -> WaitForWordInfo(contentPadding)
        else -> WordDefinition(wordInfo.value!!, modifier = contentPadding)
    }
}

@Preview(showBackground = true)
@Composable
fun WordDefinitionPreview() = SaikyoTheme(darkTheme = true) {
    val entry = WordInfo(
        word = "迷う",
        reading = "まよう",
        meaning = """
            「迷う」は、主に以下の2つの意味があります。

            方向や道筋が分からず、どうすればよいか判断できない。
            複数の選択肢があり、どれを選ぶべきか迷う。                                   
        """.trimIndent(),
        synonyms = listOf("惑う", "混乱する", "戸惑う"),
        antonyms = listOf("決断する", "断固たる", "明確な"),
        examples = listOf(
            "山道で道に迷い、しばらくの間彷徨した。",
            "複数の大学から合格通知が届き、どの大学に進学するか迷っている。",
            "恋愛感情と友情の間で揺れ動き、心を決められない。",
        )
    )
    WordDefinition(entry)
}