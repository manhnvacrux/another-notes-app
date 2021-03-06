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

package com.maltaisn.notes.ui.home

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.maltaisn.notes.MainCoroutineRule
import com.maltaisn.notes.model.MockNotesRepository
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.testNote
import com.maltaisn.notes.ui.StatusChange
import com.maltaisn.notes.ui.assertLiveDataEventSent
import com.maltaisn.notes.ui.getOrAwaitValue
import com.maltaisn.notes.ui.note.SwipeAction
import com.maltaisn.notes.ui.note.adapter.MessageItem
import com.maltaisn.notes.ui.note.adapter.NoteItem
import com.maltaisn.notes.ui.note.adapter.NoteListLayoutMode
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class HomeViewModelTest {

    private lateinit var viewModel: HomeViewModel

    private lateinit var notesRepo: MockNotesRepository
    private lateinit var prefs: PrefsManager

    private val buildTypeBehavior: BuildTypeBehavior = mock()

    
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()


    @Before
    fun before() {
        notesRepo = MockNotesRepository()
        notesRepo.addNote(testNote(id = 1, status = NoteStatus.ACTIVE, pinned = PinnedStatus.PINNED))
        notesRepo.addNote(testNote(id = 2, status = NoteStatus.ACTIVE))
        notesRepo.addNote(testNote(id = 3, status = NoteStatus.ARCHIVED))
        notesRepo.addNote(testNote(id = 4, status = NoteStatus.DELETED))

        prefs = mock {
            on { listLayoutMode } doReturn NoteListLayoutMode.LIST
            on { lastTrashReminderTime } doReturn 0
        }

        viewModel = HomeViewModel(SavedStateHandle(), notesRepo, prefs,
                buildTypeBehavior)
    }

    @Test
    fun `should show only active notes (both pinned and unpinned)`() = mainCoroutineRule.runBlockingTest {
        val note1 = notesRepo.getById(1)!!
        val note2 = notesRepo.getById(2)!!
        viewModel.setNoteStatus(NoteStatus.ACTIVE)

        assertEquals(NoteStatus.ACTIVE, viewModel.noteStatus.getOrAwaitValue())
        assertEquals(listOf(
                HomeViewModel.PINNED_HEADER_ITEM,
                NoteItem(1, note1),
                HomeViewModel.NOT_PINNED_HEADER_ITEM,
                NoteItem(2, note2)
        ), viewModel.noteItems.getOrAwaitValue())
    }

    @Test
    fun `should show only active notes (pinned only)`() = mainCoroutineRule.runBlockingTest {
        notesRepo.deleteNote(2)
        val note = notesRepo.getById(1)!!
        viewModel.setNoteStatus(NoteStatus.ACTIVE)

        assertEquals(NoteStatus.ACTIVE, viewModel.noteStatus.getOrAwaitValue())
        assertEquals(listOf(
                HomeViewModel.PINNED_HEADER_ITEM,
                NoteItem(1, note)
        ), viewModel.noteItems.getOrAwaitValue())
    }

    @Test
    fun `should show only active notes (unpinned only)`() = mainCoroutineRule.runBlockingTest {
        notesRepo.deleteNote(1)
        val note = notesRepo.getById(2)!!
        viewModel.setNoteStatus(NoteStatus.ACTIVE)

        assertEquals(NoteStatus.ACTIVE, viewModel.noteStatus.getOrAwaitValue())
        assertEquals(listOf(
                NoteItem(2, note)
        ), viewModel.noteItems.getOrAwaitValue())
    }

    @Test
    fun `should show only archived notes`() = mainCoroutineRule.runBlockingTest {
        val note = notesRepo.getById(3)!!
        viewModel.setNoteStatus(NoteStatus.ARCHIVED)

        assertEquals(NoteStatus.ARCHIVED, viewModel.noteStatus.getOrAwaitValue())
        assertEquals(listOf(
                NoteItem(3, note)
        ), viewModel.noteItems.getOrAwaitValue())
    }

    @Test
    fun `should show only deleted notes`() = mainCoroutineRule.runBlockingTest {
        val note = notesRepo.getById(4)!!
        viewModel.setNoteStatus(NoteStatus.DELETED)

        assertEquals(NoteStatus.DELETED, viewModel.noteStatus.getOrAwaitValue())
        assertEquals(listOf(
                MessageItem(-1, R.string.trash_reminder_message,
                        listOf(PrefsManager.TRASH_AUTO_DELETE_DELAY.inDays.toInt())),
                NoteItem(4, note)
        ), viewModel.noteItems.getOrAwaitValue())
    }

    @Test
    fun `should update list when data is changed`() = mainCoroutineRule.runBlockingTest {
        val firstNote = notesRepo.getById(2)!!
        viewModel.setNoteStatus(NoteStatus.ACTIVE)

        notesRepo.insertNote(testNote(status = NoteStatus.ACTIVE))
        val newNote = notesRepo.getById(notesRepo.lastId)!!

        assertEquals(listOf(
                NoteItem(newNote.id, newNote),
                NoteItem(firstNote.id, firstNote)
        ), viewModel.noteItems.getOrAwaitValue())
    }

    @Test
    fun `should update list when trash reminder item is dismissed`() = mainCoroutineRule.runBlockingTest {
        val note = notesRepo.getById(4)!!
        viewModel.setNoteStatus(NoteStatus.DELETED)
        viewModel.onMessageItemDismissed(MessageItem(-1, 0, emptyList()), 0)

        verify(prefs).lastTrashReminderTime = any()

        assertEquals(listOf(
                NoteItem(4, note)
        ), viewModel.noteItems.getOrAwaitValue())
    }

    @Test
    fun `should only allow note swipe in active notes`() = mainCoroutineRule.runBlockingTest {
        viewModel.setNoteStatus(NoteStatus.ACTIVE)
        assertTrue(viewModel.isNoteSwipeEnabled)

        viewModel.setNoteStatus(NoteStatus.ARCHIVED)
        assertFalse(viewModel.isNoteSwipeEnabled)

        viewModel.setNoteStatus(NoteStatus.DELETED)
        assertFalse(viewModel.isNoteSwipeEnabled)
    }

    @Test
    fun `should change list layout mode`() = mainCoroutineRule.runBlockingTest {
        viewModel.toggleListLayoutMode()

        verify(prefs).listLayoutMode = NoteListLayoutMode.GRID
        assertEquals(NoteListLayoutMode.GRID, viewModel.listLayoutMode.getOrAwaitValue())
    }

    @Test
    fun `should invoke build type behavior`() = mainCoroutineRule.runBlockingTest {
        viewModel.doExtraAction()
        verify(buildTypeBehavior).doExtraAction(viewModel)
    }

    @Test
    fun `should show empty trash confirm`() = mainCoroutineRule.runBlockingTest {
        viewModel.emptyTrashPre()
        assertLiveDataEventSent(viewModel.showEmptyTrashDialogEvent, Unit)
    }

    @Test
    fun `should empty trash`() = mainCoroutineRule.runBlockingTest {
        viewModel.emptyTrash()
        assertTrue(notesRepo.getNotesByStatus(NoteStatus.DELETED).first().isEmpty())
    }

    @Test
    fun `should check selected items when creating them`() = mainCoroutineRule.runBlockingTest {
        viewModel.setNoteStatus(NoteStatus.ACTIVE)
        viewModel.onNoteItemLongClicked(getNoteItemAt(1), 1)
        assertTrue(getNoteItemAt(1).checked)
    }

    @Test
    fun `should archive note on swipe`() = mainCoroutineRule.runBlockingTest {
        whenever(prefs.swipeAction) doReturn SwipeAction.ARCHIVE

        val note = notesRepo.getById(1)!!
        viewModel.setNoteStatus(NoteStatus.ACTIVE)
        viewModel.onNoteSwiped(1)

        assertEquals(NoteStatus.ARCHIVED, notesRepo.getById(1)!!.status)
        assertLiveDataEventSent(viewModel.statusChangeEvent, StatusChange(
                listOf(note), NoteStatus.ACTIVE, NoteStatus.ARCHIVED))
    }

    @Test
    fun `should delete note on swipe`() = mainCoroutineRule.runBlockingTest {
        whenever(prefs.swipeAction) doReturn SwipeAction.DELETE

        val note = notesRepo.getById(1)!!
        viewModel.setNoteStatus(NoteStatus.ACTIVE)
        viewModel.onNoteSwiped(1)

        assertEquals(NoteStatus.DELETED, notesRepo.getById(1)!!.status)
        assertLiveDataEventSent(viewModel.statusChangeEvent, StatusChange(
                listOf(note), NoteStatus.ACTIVE, NoteStatus.DELETED))
    }

    private fun getNoteItemAt(pos: Int) = viewModel.noteItems.getOrAwaitValue()[pos] as NoteItem

}
