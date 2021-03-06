/*
 * Copyright 2020 Nicolas Maltais
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:UseSerializers(DateTimeConverter::class, NoteTypeConverter::class,
        NoteStatusConverter::class, NoteMetadataConverter::class, PinnedStatusConverter::class)

package com.maltaisn.notes.model.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.maltaisn.notes.model.converter.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.*


@Serializable
@Entity(tableName = "notes")
data class Note(
        /**
         * Note ID in the database.
         */
        @PrimaryKey(autoGenerate = true)
        @ColumnInfo(name = "id")
        val id: Long,

        /**
         * Note type, determines the type of metadata.
         */
        @ColumnInfo(name = "type")
        @SerialName("type")
        val type: NoteType,

        /**
         * Note title, can be used for search.
         */
        @ColumnInfo(name = "title")
        @SerialName("title")
        val title: String,

        /**
         * Note text content, can be used for search.
         */
        @ColumnInfo(name = "content")
        @SerialName("content")
        val content: String,

        /**
         * Note metadata, not used for search.
         * @see NoteMetadataConverter
         */
        @ColumnInfo(name = "metadata")
        @SerialName("metadata")
        val metadata: NoteMetadata,

        /**
         * Creation date of the note, in UTC time.
         */
        @ColumnInfo(name = "added_date")
        @SerialName("added")
        val addedDate: Date,

        /**
         * Last modification date of the note, in UTC time.
         * Change of [status] changes last modified date too.
         */
        @ColumnInfo(name = "modified_date")
        @SerialName("modified")
        val lastModifiedDate: Date,

        /**
         * Status of the note, i.e. its location in the user interface.
         */
        @ColumnInfo(name = "status")
        @SerialName("status")
        val status: NoteStatus,

        /**
         * Describes how the note is pinned.
         * Notes with [status] set to [NoteStatus.ACTIVE] should be pinned or unpinned.
         * Other notes should be set to [PinnedStatus.CANT_PIN].
         */
        @ColumnInfo(name = "pinned")
        @SerialName("pinned")
        val pinned: PinnedStatus
) {

    init {
        // Validate the type of metadata.
        require(when (type) {
            NoteType.TEXT -> metadata is BlankNoteMetadata
            NoteType.LIST -> metadata is ListNoteMetadata
        })

        require(addedDate.time <= lastModifiedDate.time) {
            "Note added date must be before or on last modified date."
        }

        require(status != NoteStatus.ACTIVE || pinned != PinnedStatus.CANT_PIN) {
            "Active note must be pinnable."
        }
        require(status == NoteStatus.ACTIVE || pinned == PinnedStatus.CANT_PIN) {
            "Archived or deleted note must not be pinnable."
        }
    }

    /**
     * Returns `true` if note has no title and no content.
     * Metadata is not taken into account.
     */
    val isBlank: Boolean
        get() = title.isBlank() && content.isBlank()

    /**
     * If note is a list note, returns a list of the items in it.
     */
    val listItems: List<ListNoteItem>
        get() {
            check(type == NoteType.LIST) { "Cannot get list items for non-list note." }

            val checked = (metadata as ListNoteMetadata).checked
            val items = content.split('\n')
            if (items.size == 1 && checked.isEmpty()) {
                // No items
                return emptyList()
            }

            check(checked.size == items.size) { "Invalid list note data." }

            return items.mapIndexed { i, text ->
                ListNoteItem(text, checked[i])
            }
        }

    /**
     * Returns conversion of this note to a text note if it's not already one.
     * If all items were blank, resulting list note is empty. Otherwise, each item
     * because a text line with a bullet point at the start. Checked state is always lost.
     *
     * @param keepCheckedItems Whether to keep checked items or delete them.
     */
    fun asTextNote(keepCheckedItems: Boolean): Note = when (type) {
        NoteType.TEXT -> this
        NoteType.LIST -> {
            val items = listItems
            val content = if (items.all { it.content.isBlank() }) {
                // All list items are blank, so no content.
                ""
            } else {
                // Append a bullet point to each line of content.
                buildString {
                    for (item in items) {
                        if (keepCheckedItems || !item.checked) {
                            append(DEFAULT_BULLET_CHAR)
                            append(' ')
                            append(item.content)
                            append('\n')
                        }
                    }
                    if (length > 0) {
                        deleteCharAt(lastIndex)
                    }
                }
            }
            copy(type = NoteType.TEXT, content = content, metadata = BlankNoteMetadata)
        }
    }

    /**
     * Returns a conversion of this note to a list note if it's not already one.
     * Each text line becomes an unchecked list item.
     * If all lines started with a bullet point, the bullet point is removed.
     */
    fun asListNote(): Note = when (type) {
        NoteType.LIST -> this
        NoteType.TEXT -> {
            // Convert each list item to a text line.
            val lines = content.split('\n')
            val content = if (lines.all { it.isNotEmpty() && it.first() in BULLET_CHARS }) {
                // All lines start with a bullet point, remove them.
                buildString {
                    for (line in lines) {
                        append(line.substring(1).trim())
                        append('\n')
                    }
                    deleteCharAt(lastIndex)
                }
            } else {
                // List note items content are separated by line breaks, and this is already the case.
                this.content
            }
            val metadata = ListNoteMetadata(List(lines.size) { false })
            copy(type = NoteType.LIST, content = content, metadata = metadata)
        }
    }

    /**
     * Convert this note to text, including both the title and the content.
     */
    fun asText(): String {
        val textNote = asTextNote(true)
        return buildString {
            if (title.isNotBlank()) {
                append(textNote.title)
                append('\n')
            }
            append(textNote.content)
        }
    }

    companion object {
        const val NO_ID = 0L

        const val BULLET_CHARS = "-+*•–"
        const val DEFAULT_BULLET_CHAR = '-'


        /**
         * Get the title of a copy of a note with [currentTitle].
         * Localized strings [untitledName] and [copySuffix] must be provided.
         * Returns "- Copy", "- Copy 2", "- Copy 3", etc, and sets a title if current is blank.
         */
        fun getCopiedNoteTitle(currentTitle: String, untitledName: String, copySuffix: String): String {
            val match = "^(.*) - $copySuffix(?:\\s+([1-9]\\d*))?$".toRegex().find(currentTitle)
            return when {
                match != null -> {
                    val name = match.groupValues[1]
                    val number = (match.groupValues[2].toIntOrNull() ?: 1) + 1
                    "$name - $copySuffix $number"
                }
                currentTitle.isBlank() -> "$untitledName - $copySuffix"
                else -> "$currentTitle - $copySuffix"
            }
        }
    }
}

/**
 * Representation of a list note item for [Note.listItems].
 */
data class ListNoteItem(val content: String, val checked: Boolean) {

    init {
        require('\n' !in content) { "List item content cannot contain line breaks." }
    }
}
