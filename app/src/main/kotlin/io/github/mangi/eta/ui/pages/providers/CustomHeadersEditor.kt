package io.github.mangi.eta.ui.pages.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.CustomBody
import io.github.mangi.eta.data.model.CustomHeader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val bodyJson = Json { ignoreUnknownKeys = true }

/**
 * 请求头 / 请求体编辑器（键值对列表）。
 *
 * 通过 [onHeadersChange] / [onBodyChange] 回传最新列表，自身不持有持久状态，
 * 由调用方决定保存在 Provider 还是 Model 上。
 */
@Composable
internal fun CustomHttpEditor(
    headers: List<CustomHeader>,
    body: List<CustomBody>,
    onHeadersChange: (List<CustomHeader>) -> Unit,
    onBodyChange: (List<CustomBody>) -> Unit,
) {
    // 请求头编辑
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SmallTitle(stringResource(R.string.provider_custom_headers))
            TextButton(
                text = stringResource(R.string.provider_add_header),
                onClick = {
                    onHeadersChange(headers + CustomHeader(name = "", value = ""))
                },
            )
        }
        if (headers.isEmpty()) {
            Text(
                text = stringResource(R.string.provider_no_headers),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        headers.forEachIndexed { index, header ->
            HeaderRow(
                name = header.name,
                value = header.value,
                onNameChange = { name ->
                    onHeadersChange(headers.toMutableList().also { it[index] = header.copy(name = name) })
                },
                onValueChange = { value ->
                    onHeadersChange(headers.toMutableList().also { it[index] = header.copy(value = value) })
                },
                onRemove = {
                    onHeadersChange(headers.filterIndexed { i, _ -> i != index })
                },
            )
        }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    // 请求体编辑
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SmallTitle(stringResource(R.string.provider_custom_body))
            TextButton(
                text = stringResource(R.string.provider_add_body),
                onClick = {
                    onBodyChange(body + CustomBody(key = "", value = JsonNull))
                },
            )
        }
        if (body.isEmpty()) {
            Text(
                text = stringResource(R.string.provider_no_body),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        body.forEachIndexed { index, bodyItem ->
            BodyRow(
                key = bodyItem.key,
                value = bodyItem.value.toString(),
                onKeyChange = { key ->
                    onBodyChange(body.toMutableList().also { it[index] = bodyItem.copy(key = key) })
                },
                onValueChange = { value ->
                    bodyJson.parseToJsonElementOrNull(value)?.let { parsed ->
                        onBodyChange(
                            body.toMutableList().also {
                                it[index] = bodyItem.copy(value = parsed)
                            }
                        )
                    }
                },
                onRemove = {
                    onBodyChange(body.filterIndexed { i, _ -> i != index })
                },
            )
        }
    }
}

private fun Json.parseToJsonElementOrNull(value: String) =
    runCatching { parseToJsonElement(value) }.getOrNull()

@Composable
private fun HeaderRow(
    name: String,
    value: String,
    onNameChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TextField(
                value = name,
                onValueChange = onNameChange,
                label = stringResource(R.string.provider_header_name),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = value,
                onValueChange = onValueChange,
                label = stringResource(R.string.provider_header_value),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.provider_remove_header),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
    }
}

@Composable
private fun BodyRow(
    key: String,
    value: String,
    onKeyChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    var rawValue by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (bodyJson.parseToJsonElementOrNull(rawValue) != null) {
            rawValue = value
        }
    }
    val isValidJson = bodyJson.parseToJsonElementOrNull(rawValue) != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TextField(
                value = key,
                onValueChange = onKeyChange,
                label = stringResource(R.string.provider_body_key),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = rawValue,
                onValueChange = { newValue ->
                    rawValue = newValue
                    onValueChange(newValue)
                },
                label = stringResource(R.string.provider_body_value_json),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!isValidJson) {
                Text(
                    text = stringResource(R.string.provider_body_invalid_json),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.provider_remove_body),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
    }
}
