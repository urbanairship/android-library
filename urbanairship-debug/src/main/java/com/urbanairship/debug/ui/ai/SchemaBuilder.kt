/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.ai

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.SwipeToDeleteRow
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.json.AirshipJsonSchema
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import java.util.UUID

/** The kind of value a schema node describes. Mirrors `AirshipJsonSchema.ValueType`. */
internal enum class SchemaKind(val label: String) {
    String("String"),
    StringEnum("Enum"),
    Boolean("Boolean"),
    Integer("Integer"),
    Number("Number"),
    Object("Object"),
    Array("Array")
}

/** A named property of an object node. Reference type so edits mutate in place. */
internal class SchemaProperty(
    name: String,
    required: Boolean,
    val node: SchemaNodeState
) {
    val id: String = UUID.randomUUID().toString()
    var name: String by mutableStateOf(name)
    var required: Boolean by mutableStateOf(required)
}

/**
 * Editable, recursive model of an [AirshipJsonSchema] node backing the visual builder.
 *
 * A tree of reference types so the drill-down editor observes each node independently: field
 * edits update the node that owns them, while structural edits (adding/removing a property,
 * retyping the array element) update the parent's state collections.
 */
internal class SchemaNodeState(
    kind: SchemaKind = SchemaKind.String,
    descriptionText: String = "",
    choicesText: String = "",
    properties: List<SchemaProperty> = emptyList(),
    items: SchemaNodeState? = null
) {
    val id: String = UUID.randomUUID().toString()

    var kind: SchemaKind by mutableStateOf(kind)
    var descriptionText: String by mutableStateOf(descriptionText)

    /** Comma-separated choices for [SchemaKind.StringEnum]. */
    var choicesText: String by mutableStateOf(choicesText)

    /** Child properties for [SchemaKind.Object]. */
    var properties: List<SchemaProperty> by mutableStateOf(properties)

    /** Element schema for [SchemaKind.Array]. */
    var items: SchemaNodeState? by mutableStateOf(items)

    /**
     * Lazily materializes the children a newly-selected kind needs, so switching to an array
     * immediately has an element to drill into.
     */
    fun ensureChildrenForKind() {
        if (kind == SchemaKind.Array && items == null) {
            items = SchemaNodeState()
        }
    }

    /** A short one-line summary of the node's type, for list rows. */
    val typeSummary: String
        get() = when (kind) {
            SchemaKind.String -> "string"
            SchemaKind.StringEnum -> "enum"
            SchemaKind.Boolean -> "boolean"
            SchemaKind.Integer -> "integer"
            SchemaKind.Number -> "number"
            SchemaKind.Object -> "object · ${properties.size} field${if (properties.size == 1) "" else "s"}"
            SchemaKind.Array -> "array of ${items?.typeSummary ?: "…"}"
        }

    /**
     * Builds the immutable [AirshipJsonSchema] this subtree describes. Unnamed object properties
     * are dropped so a half-typed row never produces an empty key.
     */
    fun build(): AirshipJsonSchema {
        val trimmedDescription = descriptionText.trim()
        val description = trimmedDescription.ifEmpty { null }

        return when (kind) {
            SchemaKind.String -> AirshipJsonSchema.string(description = description)
            SchemaKind.StringEnum -> {
                val choices = choicesText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                AirshipJsonSchema.string(choices = choices.ifEmpty { null }, description = description)
            }
            SchemaKind.Boolean -> AirshipJsonSchema.boolean(description = description)
            SchemaKind.Integer -> AirshipJsonSchema.integer(description = description)
            SchemaKind.Number -> AirshipJsonSchema.number(description = description)
            SchemaKind.Object -> {
                val props = LinkedHashMap<String, AirshipJsonSchema>()
                val required = mutableListOf<String>()
                properties.forEach { property ->
                    val name = property.name.trim()
                    if (name.isEmpty()) return@forEach
                    props[name] = property.node.build()
                    if (property.required) required.add(name)
                }
                AirshipJsonSchema.obj(
                    properties = props,
                    required = required.ifEmpty { null },
                    description = description
                )
            }
            SchemaKind.Array -> AirshipJsonSchema.array(
                items = (items ?: SchemaNodeState()).build(),
                description = description
            )
        }
    }

    /**
     * Replaces this node in place with a flat object of the given properties, so the observing
     * editor updates without the tree object being swapped out. Used to drop in an AI-suggested
     * schema.
     */
    fun applyObject(properties: List<SchemaProperty>) {
        kind = SchemaKind.Object
        descriptionText = ""
        choicesText = ""
        items = null
        this.properties = properties
    }

    /**
     * Captures this subtree as a value type so a schema can be persisted with the rest of a
     * sandbox's state (see [SchemaNodeSnapshot]).
     */
    fun snapshot(): SchemaNodeSnapshot = SchemaNodeSnapshot(
        kind = kind.name,
        descriptionText = descriptionText,
        choicesText = choicesText,
        properties = properties.map {
            SchemaNodeSnapshot.PropertySnapshot(name = it.name, required = it.required, node = it.node.snapshot())
        },
        items = items?.let { listOf(it.snapshot()) } ?: emptyList()
    )

    /**
     * Replaces this node in place from a snapshot, so an observing editor updates without the
     * root object being swapped out.
     */
    fun apply(snapshot: SchemaNodeSnapshot) {
        kind = try {
            SchemaKind.valueOf(snapshot.kind)
        } catch (e: IllegalArgumentException) {
            SchemaKind.String
        }
        descriptionText = snapshot.descriptionText
        choicesText = snapshot.choicesText
        properties = snapshot.properties.map {
            SchemaProperty(name = it.name, required = it.required, node = from(it.node))
        }
        items = snapshot.items.firstOrNull()?.let { from(it) }
    }

    companion object {
        /** Builds a fresh node tree from a snapshot. */
        fun from(snapshot: SchemaNodeSnapshot): SchemaNodeState {
            val node = SchemaNodeState()
            node.apply(snapshot)
            return node
        }

        /** Seeds the builder with the executor's default `{ result, reason }` contract. */
        fun defaultContract(): SchemaNodeState = SchemaNodeState(
            kind = SchemaKind.Object,
            properties = listOf(
                SchemaProperty(
                    name = "result",
                    required = true,
                    node = SchemaNodeState(descriptionText = "The value the instruction asks for")
                ),
                SchemaProperty(
                    name = "reason",
                    required = true,
                    node = SchemaNodeState(descriptionText = "Brief reason for the result")
                )
            )
        )
    }
}

/**
 * A JSON-serializable snapshot of a [SchemaNodeState] subtree, so a built schema can be
 * saved/restored with the rest of a sandbox's state. [items] holds zero or one element (an
 * array node's element schema) to stay trivially serializable.
 */
internal data class SchemaNodeSnapshot(
    val kind: String,
    val descriptionText: String = "",
    val choicesText: String = "",
    val properties: List<PropertySnapshot> = emptyList(),
    val items: List<SchemaNodeSnapshot> = emptyList()
) {
    data class PropertySnapshot(
        val name: String,
        val required: Boolean,
        val node: SchemaNodeSnapshot
    )

    companion object {
        /** The `{ result, reason }` default contract, so it can seed persisted state directly. */
        fun defaultContract(): SchemaNodeSnapshot = SchemaNodeState.defaultContract().snapshot()
    }
}

// region JSON (for AirshipDebugAIStore-backed persistence)

internal fun SchemaNodeSnapshot.toJson(): JsonValue = jsonMapOf(
    "kind" to kind,
    "description_text" to descriptionText,
    "choices_text" to choicesText,
    "properties" to JsonValue.wrapOpt(properties.map { it.toJson() }),
    "items" to JsonValue.wrapOpt(items.map { it.toJson() })
).toJsonValue()

private fun SchemaNodeSnapshot.PropertySnapshot.toJson(): JsonValue = jsonMapOf(
    "name" to name,
    "required" to required,
    "node" to node.toJson()
).toJsonValue()

internal fun schemaNodeSnapshotFromJson(json: JsonValue): SchemaNodeSnapshot? = try {
    val map = json.requireMap()
    SchemaNodeSnapshot(
        kind = map.requireField("kind"),
        descriptionText = map.optionalField<String>("description_text") ?: "",
        choicesText = map.optionalField<String>("choices_text") ?: "",
        properties = (map.get("properties")?.list ?: emptyList()).mapNotNull(::propertySnapshotFromJson),
        items = (map.get("items")?.list ?: emptyList()).mapNotNull(::schemaNodeSnapshotFromJson)
    )
} catch (e: JsonException) {
    null
}

private fun propertySnapshotFromJson(json: JsonValue): SchemaNodeSnapshot.PropertySnapshot? = try {
    val map = json.requireMap()
    val node = schemaNodeSnapshotFromJson(map.require("node"))
    if (node == null) {
        null
    } else {
        SchemaNodeSnapshot.PropertySnapshot(
            name = map.requireField("name"),
            required = map.requireField("required"),
            node = node
        )
    }
} catch (e: JsonException) {
    null
}

// endregion

// region Drill-down editor

private data class SchemaFrame(
    val node: SchemaNodeState,
    val property: SchemaProperty?,
    val title: String
)

/** Full-screen modal hosting the recursive schema tree editor, with its own back-stack. */
@Composable
internal fun SchemaEditorDialog(root: SchemaNodeState, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val backStack = remember { mutableStateListOf<SchemaFrame>() }

            BackHandler {
                if (backStack.isEmpty()) onDismiss() else backStack.removeAt(backStack.lastIndex)
            }

            val frame = backStack.lastOrNull() ?: SchemaFrame(node = root, property = null, title = "Output Schema")

            SchemaNodeEditorScreen(
                frame = frame,
                onNavigateUp = { if (backStack.isEmpty()) onDismiss() else backStack.removeAt(backStack.lastIndex) },
                onDrillInto = { newFrame -> backStack.add(newFrame) }
            )
        }
    }
}

/**
 * One screen of the schema tree. Objects list their properties in a standard editable list
 * (swipe to remove; `+` opens a dialog to add); each property drills into its own screen.
 */
@Composable
private fun SchemaNodeEditorScreen(
    frame: SchemaFrame,
    onNavigateUp: () -> Unit,
    onDrillInto: (SchemaFrame) -> Unit
) {
    val node = frame.node
    var showingAddProperty by remember { mutableStateOf(false) }
    var showingKindMenu by remember { mutableStateOf(false) }

    DebugScreen(title = frame.title, navigation = TopBarNavigation.Back(onNavigateUp)) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            frame.property?.let { property ->
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Property", style = MaterialTheme.typography.labelMedium)
                        TextField(
                            value = property.name,
                            onValueChange = { property.name = it },
                            label = { Text("Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Required", modifier = Modifier.padding(end = 8.dp))
                            Switch(checked = property.required, onCheckedChange = { property.required = it })
                        }
                    }
                    HorizontalDivider()
                }
            }

            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Type", style = MaterialTheme.typography.labelMedium)
                    Box(modifier = Modifier.padding(top = 4.dp)) {
                        OutlinedButton(onClick = { showingKindMenu = true }) {
                            Text(node.kind.label)
                        }
                        DropdownMenu(expanded = showingKindMenu, onDismissRequest = { showingKindMenu = false }) {
                            SchemaKind.entries.forEach { kind ->
                                DropdownMenuItem(
                                    text = { Text(kind.label) },
                                    onClick = {
                                        node.kind = kind
                                        node.ensureChildrenForKind()
                                        showingKindMenu = false
                                    }
                                )
                            }
                        }
                    }

                    TextField(
                        value = node.descriptionText,
                        onValueChange = { node.descriptionText = it },
                        label = { Text("Description (optional)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )

                    if (node.kind == SchemaKind.StringEnum) {
                        TextField(
                            value = node.choicesText,
                            onValueChange = { node.choicesText = it },
                            label = { Text("Choices, comma-separated") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }
                }
                HorizontalDivider()
            }

            when (node.kind) {
                SchemaKind.Object -> {
                    item {
                        Text(
                            text = "PROPERTIES",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    if (node.properties.isEmpty()) {
                        item {
                            Text(
                                text = "No properties — tap + Add property below",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    items(count = node.properties.size) { index ->
                        val property = node.properties[index]
                        SwipeToDeleteRow(
                            content = {
                                ListItem(
                                    modifier = Modifier.clickable {
                                        onDrillInto(
                                            SchemaFrame(
                                                node = property.node,
                                                property = property,
                                                title = property.name.ifEmpty { "Property" }
                                            )
                                        )
                                    },
                                    headlineContent = {
                                        Text(property.name.ifEmpty { "(unnamed)" })
                                    },
                                    supportingContent = { Text(property.node.typeSummary) },
                                    trailingContent = {
                                        if (property.required) {
                                            Text(
                                                text = "required",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                )
                            },
                            onDelete = {
                                node.properties = node.properties.filterNot { it.id == property.id }
                            }
                        )
                        HorizontalDivider()
                    }

                    item {
                        TextButton(
                            onClick = { showingAddProperty = true },
                            modifier = Modifier.padding(16.dp)
                        ) { Text("+ Add property") }
                    }
                }

                SchemaKind.Array -> {
                    item {
                        Text(
                            text = "ARRAY ELEMENT",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        node.items?.let { element ->
                            ListItem(
                                modifier = Modifier.clickable {
                                    onDrillInto(SchemaFrame(node = element, property = null, title = "Element"))
                                },
                                headlineContent = { Text("Element schema") },
                                trailingContent = { Text(element.typeSummary) }
                            )
                            HorizontalDivider()
                        }
                    }
                }

                else -> Unit
            }
        }
    }

    if (showingAddProperty) {
        AddPropertyDialog(
            onAdd = { name, kind, required, description ->
                val child = SchemaNodeState(kind = kind, descriptionText = description)
                child.ensureChildrenForKind()
                node.properties = node.properties + SchemaProperty(name = name, required = required, node = child)
                showingAddProperty = false
            },
            onDismiss = { showingAddProperty = false }
        )
    }
}

/** Dialog for defining a new object property before it's appended. */
@Composable
private fun AddPropertyDialog(
    onAdd: (name: String, kind: SchemaKind, required: Boolean, description: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(SchemaKind.String) }
    var required by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var showingKindMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Property") },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Property name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Box(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { showingKindMenu = true }) { Text(kind.label) }
                    DropdownMenu(expanded = showingKindMenu, onDismissRequest = { showingKindMenu = false }) {
                        SchemaKind.entries.forEach { entry ->
                            DropdownMenuItem(
                                text = { Text(entry.label) },
                                onClick = {
                                    kind = entry
                                    showingKindMenu = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Required", modifier = Modifier.padding(end = 8.dp))
                    Switch(checked = required, onCheckedChange = { required = it })
                }

                TextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name.trim(), kind, required, description) },
                enabled = name.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// endregion
