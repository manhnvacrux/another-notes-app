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

package com.maltaisn.notes.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistryOwner
import kotlin.reflect.KClass


interface AssistedSavedStateViewModelFactory<T> {
    fun create(savedStateHandle: SavedStateHandle): T
}

@MainThread
inline fun <reified VM : ViewModel> ComponentActivity.viewModel(
        noinline provider: (SavedStateHandle) -> VM
) = createLazyViewModel(
        viewModelClass = VM::class,
        savedStateRegistryOwnerProducer = { this },
        viewModelStoreOwnerProducer = { this },
        viewModelProvider = provider
)

@MainThread
inline fun <reified VM : ViewModel> Fragment.viewModel(
        noinline provider: (SavedStateHandle) -> VM
) = createLazyViewModel(
        viewModelClass = VM::class,
        savedStateRegistryOwnerProducer = { this },
        viewModelStoreOwnerProducer = { this },
        viewModelProvider = provider
)

@MainThread
inline fun <reified VM : ViewModel> Fragment.activityViewModel(
        noinline provider: (SavedStateHandle) -> VM
) = createLazyViewModel(
        viewModelClass = VM::class,
        savedStateRegistryOwnerProducer = { requireActivity() },
        viewModelStoreOwnerProducer = { requireActivity() },
        viewModelProvider = provider
)

fun <VM : ViewModel> createLazyViewModel(
        viewModelClass: KClass<VM>,
        savedStateRegistryOwnerProducer: () -> SavedStateRegistryOwner,
        viewModelStoreOwnerProducer: () -> ViewModelStoreOwner,
        viewModelProvider: (SavedStateHandle) -> VM
) = ViewModelLazy(viewModelClass, { viewModelStoreOwnerProducer().viewModelStore }) {
    object : AbstractSavedStateViewModelFactory(savedStateRegistryOwnerProducer(), Bundle()) {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(
                key: String,
                modelClass: Class<T>,
                handle: SavedStateHandle
        ) = viewModelProvider(handle) as T
    }
}
