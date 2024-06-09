package com.shio.saikyo.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shio.saikyo.R
import com.shio.saikyo.ui.primitives.DefaultAppBar
import com.shio.saikyo.ui.primitives.HorizontalWhitespace
import com.shio.saikyo.ui.primitives.NavBackButton
import com.shio.saikyo.ui.primitives.NavCancelButton
import com.shio.saikyo.ui.primitives.VerticalWhitespace
import com.shio.saikyo.ui.theme.SaikyoTheme
import com.shio.saikyo.util.verticalScrollbar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class OpenPersistableDocumentTree : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent {
        return super.createIntent(context, input).apply {
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
    }
}

@Composable
fun LocalMediaDescription(
    modifier: Modifier = Modifier
) = Text(
    stringResource(R.string.local_media_desc_template).replace("$$", stringResource(R.string.app_name)),
    modifier = modifier
)

@Composable
fun AccessibleFolderItem(
    folder: AccessibleFolder,
    onClick: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable () -> Unit = {
        Icon(painterResource(id = R.drawable.folder_24dp_fill), null)
    }
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
) {
    leadingIcon()
    HorizontalWhitespace(width = 8.dp)
    Text(folder.name, modifier = Modifier.weight(1f))
    Checkbox(checked = folder.isEnabled, onCheckedChange = onClick)
}

@Composable
fun AccessibleFolderItem(
    folder: AccessibleFolder,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable () -> Unit = {
        Icon(painterResource(id = R.drawable.folder_24dp_fill), null)
    }
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
) {
    leadingIcon()
    HorizontalWhitespace(width = 8.dp)
    Text(folder.name, modifier = Modifier.weight(1f))
}

@Composable
fun AccessibleFolderList(
    folders: List<AccessibleFolder>,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 0.dp,
    itemContent: @Composable (LazyItemScope.(item: AccessibleFolder) -> Unit)
) {
    val ls = rememberLazyListState()
    LazyColumn(
        state = ls,
        contentPadding = PaddingValues(contentPadding),
        modifier = modifier.verticalScrollbar(ls)
    ) {
        items(folders, key = { it.uri }, itemContent = itemContent)
    }
}

@Composable
fun AddFolderButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = Button(
    onClick = onClick,
    modifier = modifier
) {
    val desc = stringResource(R.string.add_local_media_folder_desc)
    Icon(Icons.Default.Add, contentDescription = desc)
    Text(desc)
}

@Composable
fun DeleteFolderButton(onClick: () -> Unit, modifier: Modifier) = Button(
    onClick = onClick,
    modifier = modifier
) {
    val desc = stringResource(R.string.delete_local_media_folder_desc)
    Icon(Icons.Default.Delete, contentDescription = desc)
    Text(desc)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalMediaSelectionScreen(
    onNavBack: () -> Unit,
    accessibleFolders: List<AccessibleFolder>,
    onFolderAdded: (Uri) -> Unit,
    onToggleFolderEnabled: (AccessibleFolder) -> Unit,
    onAnyFolderLongClick: () -> Unit,
) = Scaffold(
    topBar = {
        DefaultAppBar(
            title = stringResource(R.string.local_file_access_settings_title),
            navigation = { NavBackButton(onClick = onNavBack) }
        )
    }
) { insets ->
    val ctx = LocalContext.current
    val selectFolderLauncher = rememberLauncherForActivityResult(OpenPersistableDocumentTree()) {
        if (it != null) {
            ctx.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            onFolderAdded(it)
        }
    }

    Column(modifier = Modifier.padding(insets)) {
        LocalMediaDescription(
            modifier = Modifier
                .padding(top = 16.dp)
                .padding(horizontal = 16.dp)
        )
        VerticalWhitespace(height = 8.dp)
        AccessibleFolderList(
            folders = accessibleFolders,
            contentPadding = 16.dp,
            modifier = Modifier.weight(1f, fill = false)
        ) { folder ->
            AccessibleFolderItem(
                folder = folder,
                onClick = { onToggleFolderEnabled(folder) },
                modifier = Modifier.combinedClickable(
                    onLongClick = onAnyFolderLongClick,
                    onClick = { onToggleFolderEnabled(folder) }
                )
            )
            HorizontalDivider()
        }
        VerticalWhitespace(height = 8.dp)
        AddFolderButton(
            onClick = { selectFolderLauncher.launch(null) },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(16.dp)
        )
    }
}

@Composable
fun LocalMediaSelectionScreen(
    onCancel: () -> Unit,
    accessibleFolders: List<AccessibleFolder>,
) = Scaffold(
    topBar = {
        DefaultAppBar(
            navigation = { NavCancelButton(onClick = onCancel) }
        )
    }
) { insets ->
    Column(modifier = Modifier.padding(insets)) {
        LocalMediaDescription()
        VerticalWhitespace(height = 8.dp)
        AccessibleFolderList(
            folders = accessibleFolders,
            contentPadding = 16.dp,
            modifier = Modifier.weight(1f, fill = false)
        ) { folder ->
            AccessibleFolderItem(
                folder = folder,
                modifier = Modifier.clickable {  }
            )
            HorizontalDivider()
        }
        VerticalWhitespace(height = 8.dp)
        DeleteFolderButton(
            onClick = {

            },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(16.dp)
        )
    }
}


@Composable
fun LocalMediaSelectionScreen(
    navBack: () -> Unit,
    vm: LocalMediaVM = viewModel(factory = LocalMediaVM.factory())
) {
    val uiState by vm.uiState.collectAsState()

    if (uiState.inDeleteMode) {
        LocalMediaSelectionScreen(
            onCancel = { vm.toggleDeleteMode() },
            accessibleFolders = uiState.accessibleFolders
        )
    } else {
        LocalMediaSelectionScreen(
            onNavBack = navBack,
            accessibleFolders = uiState.accessibleFolders,
            onFolderAdded = { vm.addFolder(it) },
            onAnyFolderLongClick = { vm.toggleDeleteMode() },
            onToggleFolderEnabled = { vm.toggleFolderEnabled(it) }
        )
    }

}

data class AccessibleFolder(
    val name: String,
    val uri: String,
    val isEnabled: Boolean
)

data class LocalMediaUIState(
    val accessibleFolders: List<AccessibleFolder> = listOf(),
    val inDeleteMode: Boolean = false
)

class LocalMediaVM : ViewModel() {
    private var _uiState = MutableStateFlow(LocalMediaUIState())
    val uiState = _uiState.asStateFlow()

    fun addFolder(uri: Uri) {

    }

    fun toggleFolderEnabled(folder: AccessibleFolder) {

    }

    fun toggleDeleteMode() {
        _uiState.update {
            it.copy(inDeleteMode = !it.inDeleteMode)
        }
    }

    companion object {
        fun factory() = viewModelFactory {
            initializer {
                LocalMediaVM()
            }
        }
    }
}


@Preview
@Composable
private fun TheThing() = SaikyoTheme {
    LocalMediaSelectionScreen(
        {},
        listOf(
            AccessibleFolder("swag", "sdf", true),
            AccessibleFolder("swag1", "sdf2", false),
            AccessibleFolder("swag2", "sdf3", true),
            AccessibleFolder("swag2", "sdf4", true),
            AccessibleFolder("swag2", "sdf5", true),
            AccessibleFolder("swag2", "sdf6", true),
            AccessibleFolder("swag2", "sdf7", true),
            AccessibleFolder("swag2", "sdf8", true),
            AccessibleFolder("swag2", "sdf9", true),
            AccessibleFolder("swag2", "sdf3dsf", true),
            AccessibleFolder("swag2", "sdfsdf3", true),
            AccessibleFolder("swag2", "sddfsgf3", true),
            AccessibleFolder("swag2", "sdsdGf3", true),
            AccessibleFolder("swag2", "sdfAD3", true),
            AccessibleFolder("swag2", "sdadff3", true),
            AccessibleFolder("swag2", "sdh", true),
            AccessibleFolder("swag2", "sddfhf3", true),
            AccessibleFolder("swag2", "sdsfghf3", true),
            AccessibleFolder("swag2", "sdsdfbf3", true),
            AccessibleFolder("swag2", "sdbsdff3", true),
            AccessibleFolder("swag2", "sdsbdaf3", true),
            AccessibleFolder("swag2", "ssbddf3", true),
        ),
        {},
        {},
        {}
    )
}