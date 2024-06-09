package com.shio.saikyo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarColors
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.paging.map
import com.shio.saikyo.R
import com.shio.saikyo.SaikyoApp
import com.shio.saikyo.ai.AIAssistant
import com.shio.saikyo.db.LemmaDAO
import com.shio.saikyo.db.LemmaEntry
import com.shio.saikyo.db.WordDAO
import com.shio.saikyo.ui.primitives.CenterActionBar
import com.shio.saikyo.ui.primitives.DefaultAppBar
import com.shio.saikyo.ui.primitives.LazyColumnScrollbar
import com.shio.saikyo.ui.primitives.NavBackButton
import com.shio.saikyo.ui.primitives.ProgressNextButton
import com.shio.saikyo.ui.text.TextField
import com.shio.saikyo.ui.theme.SaikyoTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

@Composable
fun rememberSearchHits(
    state: SearchUIState,
    isDownloading: (LemmaEntry) -> Boolean,
    onFetchMeaning: (LemmaEntry) -> Unit,
    onCancelFetchMeaning: (LemmaEntry) -> Unit,
    onClick: (LemmaEntry) -> Unit
) = remember(state, isDownloading, onFetchMeaning, onCancelFetchMeaning, onCancelFetchMeaning) {
    state.hits.map {
        it.map { l ->
            when {
                isDownloading(l) -> SearchHit.Downloading(
                    l.lemmaId, l.kanji, l.kana, onClick = { onCancelFetchMeaning(l) }
                )

                l.meaning == null -> SearchHit.Untouched(
                    l.lemmaId, l.kanji, l.kana, onClick = { onFetchMeaning(l) }
                )

                else -> SearchHit.Hydrated(l.lemmaId, l.kanji, l.kana, l.meaning, onClick = { onClick(l) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchInputBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge.merge(
        color = LocalContentColor.current
    ),
    placeholder: @Composable() (() -> Unit)? = null,
    trailingIcon: @Composable() (() -> Unit)? = null,
    colors: SearchBarColors = SearchBarDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) = BasicTextField(
    value = query,
    onValueChange = onQueryChange,
    singleLine = true,
    modifier = modifier,
    cursorBrush = SolidColor(colors.inputFieldColors.cursorColor),
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions = KeyboardActions(onSearch = {
        onSearch(query)
    }),
    interactionSource = interactionSource,
    textStyle = textStyle,
    decorationBox = { innerTextField ->
        TextFieldDefaults.DecorationBox(
            value = query,
            innerTextField = innerTextField,
            enabled = true,
            singleLine = true,
            placeholder = placeholder,
            trailingIcon = trailingIcon,
            visualTransformation = VisualTransformation.None,
            interactionSource = interactionSource,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            colors = colors.inputFieldColors,
            container = {
                Box(
                    modifier = Modifier
                        .widthIn(min = TextFieldDefaults.MinWidth)
                        .background(
                            color = TextFieldDefaults.colors().unfocusedContainerColor,
                            shape = RoundedCornerShape(50)
                        )
                )
            }
        )


    }
)

@Composable
fun ResetSearchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = IconButton(onClick, modifier = modifier) {
    Icon(
        imageVector = Icons.Default.Close,
        contentDescription = "Button to erase all text from the search field"
    )
}

@Composable
fun DownloadButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = IconButton(onClick = onClick, modifier = modifier) {
    Icon(
        painter = painterResource(id = R.drawable.download_for_offline_24dp_fill),
        contentDescription = null,
        modifier = Modifier.padding(horizontal = 8.dp)
    )
}

@Composable
fun CancelButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
) {
    CircularProgressIndicator(
        color = MaterialTheme.colorScheme.primary,
        strokeWidth = 2.dp,
        modifier = Modifier.size(24.dp)
    )
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
    }
}

sealed class SearchHit(
    val id: Int,
    val kanji: String,
    val kana: String,
    val onClick: () -> Unit,
) {
    class Untouched(
        id: Int,
        kanji: String,
        kana: String,
        onClick: () -> Unit,
    ) : SearchHit(id, kanji, kana, onClick)

    class Downloading(
        id: Int,
        kanji: String,
        kana: String,
        onClick: () -> Unit,
    ) : SearchHit(id, kanji, kana, onClick)

    class Hydrated(
        id: Int,
        kanji: String,
        kana: String,
        val meaning: String,
        onClick: () -> Unit,
    ) : SearchHit(id, kanji, kana, onClick)
}

@Composable
fun SearchHitCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (ColumnScope.() -> Unit)
) = Card(
    shape = MaterialTheme.shapes.extraSmall,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
    modifier = modifier
        .fillMaxSize()
        .clickable(onClick = onClick),
) {
    Spacer(modifier = Modifier.padding(top = 8.dp))
    content()
    Spacer(modifier = Modifier.padding(4.dp))
    HorizontalDivider()
}

fun koumoku(kanji: String, kana: String) = buildAnnotatedString {
    val boldText = SpanStyle(fontWeight = FontWeight.Bold)

    withStyle(boldText) {
        append(kanji)
    }
    append(", ")

    withStyle(boldText) {
        append(kana)
    }
}

@Composable
fun SearchHitItem(
    entry: SearchHit.Hydrated,
    modifier: Modifier = Modifier
) = SearchHitCard(entry.onClick, modifier = modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                koumoku(entry.kanji, entry.kana),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = entry.meaning,
                style = MaterialTheme.typography.bodySmall,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )

        }

        ProgressNextButton(onClick = entry.onClick)
    }
}

@Composable
fun SearchHitItem(
    entry: SearchHit.Untouched,
    modifier: Modifier = Modifier
) = SearchHitCard(entry.onClick, modifier = modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                koumoku(entry.kanji, entry.kana),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        DownloadButton(entry.onClick)
    }
}

@Composable
fun SearchHitItem(
    entry: SearchHit.Downloading,
    modifier: Modifier = Modifier
) = SearchHitCard(onClick = entry.onClick, modifier = modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                koumoku(entry.kanji, entry.kana),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        CancelButton(entry.onClick)
    }
}

@Composable
fun SearchHitList(
    hits: LazyPagingItems<SearchHit>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp)
) {
    LazyColumnScrollbar(
        contentPadding = contentPadding,
        modifier = modifier
            .padding(top = 8.dp)
            .fillMaxSize()
    ) {
        items(hits.itemCount, key = hits.itemKey { it.id }) {
            when (val entry = hits[it]!!) {
                is SearchHit.Untouched -> SearchHitItem(entry)

                is SearchHit.Downloading -> SearchHitItem(entry)

                is SearchHit.Hydrated -> SearchHitItem(entry)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DictionaryLiteScreen(
    navBack: () -> Unit,
    ctxMenu: ContextMenu,
    query: String,
    onQueryChange: (String) -> Unit,
    hits: LazyPagingItems<SearchHit>,
) {
    Scaffold(
        topBar = {
            DefaultAppBar(
                navigation = { NavBackButton(navBack) },
            )
        },
    ) { contentPadding ->
        val fm = LocalFocusManager.current

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) {
                    // when searching with the search field, clicking off of it will release focus
                    fm.clearFocus()
                }
        ) {
            TextField(
                value = query,
                // TODO: make my own selectable text composable...
//                contextMenu = ctxMenu,
                onValueChange = onQueryChange,
                modifier = Modifier.onInterceptKeyBeforeSoftKeyboard {
                    if (it.key == Key.Back) {
                        fm.clearFocus()
                    }

                    return@onInterceptKeyBeforeSoftKeyboard false
                },
                label = { Text(stringResource(R.string.dictionary_empty_search_label)) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        ResetSearchButton(onClick = { onQueryChange("") })
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { fm.clearFocus() }),
                maxLines = 3
            )

            SearchHitList(
                hits,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxSize()
            )
        }
    }
}

@Composable
fun DictionaryLiteScreen(
    prompt: String,
    navToDefinition: (Int) -> Unit,
    navBack: () -> Unit,
    vm: DictVM = viewModel(factory = DictVM.Factory(prompt))
) {
    val uiState by vm.searchState.collectAsState()
    val ctxMenu = ContextMenu.from(
        ContextMenuItem.Copy,
        ContextMenuItem.Search(LocalContext.current),
        ContextMenuItem.SelectAll,
        *LocalTextProcessors.current,
        maxVisible = 2
    )

    val searchHits = rememberSearchHits(
        state = uiState,
        isDownloading = { vm.isFetchingMeaningOf(it) },
        onFetchMeaning = { vm.fetchMeaningOf(it) },
        onCancelFetchMeaning = { vm.cancelFetchMeaningOf(it) },
        onClick = { navToDefinition(it.lemmaId) }
    )

    DictionaryLiteScreen(
        navBack = navBack,
        ctxMenu = ctxMenu,
        query = uiState.term,
        onQueryChange = { vm.setSearchTerm(it) },
        hits = searchHits.collectAsLazyPagingItems()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dictionary2Screen(
    navBack: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    hits: LazyPagingItems<SearchHit>,
) {
    Scaffold(
        topBar = {
            CenterActionBar(
                navigation = {
                    NavBackButton(onClick = navBack)
                },
                center = {
                    SearchInputBar(
                        query = query,
                        onQueryChange = onQueryChange,
                        onSearch = onQueryChange,
                        placeholder = { Text(stringResource(R.string.dictionary_empty_search_label)) },
                        trailingIcon = {
                           if (query.isNotBlank()) {
                               ResetSearchButton(onClick = { onQueryChange("") })
                           }
                        },
                        modifier = Modifier.weight(1f),
                    )
                })
        }
    ) { insets ->
        Column(
            modifier = Modifier.padding(insets)
        ) {
            SearchHitList(
                hits,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxSize()
            )
        }
    }
}

@Composable
fun Dictionary2Screen(
    navToDefinition: (Int) -> Unit,
    navBack: () -> Unit,
    vm: DictVM = viewModel(factory = DictVM.Factory())
) {
    val uiState by vm.searchState.collectAsState()
    val searchHits = rememberSearchHits(
        state = uiState,
        isDownloading = { vm.isFetchingMeaningOf(it) },
        onFetchMeaning = { vm.fetchMeaningOf(it) },
        onCancelFetchMeaning = { vm.cancelFetchMeaningOf(it) },
        onClick = { navToDefinition(it.lemmaId) }
    )

    Dictionary2Screen(
        navBack = navBack,
        query = uiState.term,
        onQueryChange = {
            vm.setSearchTerm(it)
        },
        searchHits.collectAsLazyPagingItems(),
    )
}

data class SearchUIState(
    val term: String = "",
    val hits: Flow<PagingData<LemmaEntry>> = flow {},
    val activeDownloads: Map<Int, Job> = mapOf()
) {
    companion object {
        val Default = SearchUIState()
    }
}

class DictVM(
    val lemmas: LemmaDAO,
    val words: WordDAO,
    private val ai: AIAssistant,
) : ViewModel() {
    private var searchCollectionJob: Job? = null

    private var _searchState = MutableStateFlow(SearchUIState.Default)
    val searchState = _searchState.asStateFlow()

    fun setSearchTerm(newTerm: String = "") {
        searchCollectionJob?.cancel()
        if (newTerm.isNotBlank()) {
            _searchState.value = SearchUIState(
                term = newTerm,
                hits = Pager(PagingSettings) {
                    // TODO: figure out why this fixed the search field bug
                    lemmas.getEntriesMatchingPaged(newTerm.trim())
                }.flow.onStart {
                    searchCollectionJob = currentCoroutineContext().job
                }.cachedIn(viewModelScope)
            )
        } else {
            _searchState.value = SearchUIState.Default
        }
    }

    fun fetchMeaningOf(entry: LemmaEntry) {
        val lemmaId = entry.lemmaId

        val job = viewModelScope.launch {
            when (val info = ai.getLinguisticInfoOn(entry.kanji, entry.kana)) {
                null -> {
                    // TODO: proper error handling (e.g. usage metrics exceeds free tier)
                    lemmas.updateLemmaMeaning(lemmaId, "Download failed. Delete and try again.")
                }

                else -> {
                    words.updateLemma(lemmaId, info)
                }
            }
            cancelFetchMeaningOf(entry)
        }

        _searchState.update {
            it.copy(activeDownloads = it.activeDownloads.plus(lemmaId to job))
        }
    }

    fun isFetchingMeaningOf(entry: LemmaEntry): Boolean = _searchState.value.activeDownloads.contains(
        entry.lemmaId
    )

    fun cancelFetchMeaningOf(entry: LemmaEntry) {
        val lemmaId = entry.lemmaId

        _searchState.update {
            it.activeDownloads[lemmaId]?.cancel()
            it.copy(activeDownloads = it.activeDownloads.minus(lemmaId))
        }
    }

    companion object {
        val PagingSettings = PagingConfig(
            10,
            enablePlaceholders = false,
            initialLoadSize = 30,
        )

        fun Factory(prompt: String) = viewModelFactory {
            initializer {
                val app = (this[APPLICATION_KEY] as SaikyoApp)
                DictVM(app.db.lemmas(), app.db.words(), app.ai).apply {
                    setSearchTerm(prompt)
                }
            }
        }

        fun Factory() = viewModelFactory {
            initializer {
                val app = (this[APPLICATION_KEY] as SaikyoApp)
                DictVM(
                    app.db.lemmas(),
                    app.db.words(),
                    app.ai
                )
            }
        }
    }
}

@Preview
@Composable
private fun Dict2Preview() = SaikyoTheme {
    val fake = PagingData.from<SearchHit>(buildList {
        add(SearchHit.Untouched(2, "最強", "さいきょう", {}))
        add(SearchHit.Downloading(3, "最", "さいきょう", {}))
        add(SearchHit.Hydrated(4, "強", "さいきょう", "かっこいい", {}))
    })

    Dictionary2Screen(
        {}, "sdf", {}, MutableStateFlow(fake).collectAsLazyPagingItems()
    )
}

@Preview
@Composable
private fun DictLitePreview() = SaikyoTheme {
    val fake = PagingData.from<SearchHit>(buildList {
        add(SearchHit.Untouched(2, "最強", "さいきょう", {}))
        add(SearchHit.Downloading(3, "最", "さいきょう", {}))
        add(SearchHit.Hydrated(4, "強", "さいきょう", "かっこいい", {}))
    })

    DictionaryLiteScreen(
        navBack = { },
        ctxMenu = ContextMenu(listOf(ContextMenuItem.Copy)),
        query = "最強",
        onQueryChange = {},
        hits = MutableStateFlow(fake).collectAsLazyPagingItems()
    )
}