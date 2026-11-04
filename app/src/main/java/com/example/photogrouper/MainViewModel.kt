package com.example.photogrouper

import android.app.Application
import android.util.Size
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var photos: List<Photo> = emptyList()
    private var vectors: Map<Long, Map<String, Float>> = emptyMap()

    fun scan() {
        if (_state.value.busy) return
        viewModelScope.launch(Dispatchers.Default) {
            val ctx = getApplication<Application>()
            _state.update { it.copy(busy = true, message = "", groups = emptyList(), progress = 0) }

            photos = PhotoRepo.load(ctx)
            _state.update { it.copy(total = photos.size) }

            val labeler = ImageLabeling.getClient(
                ImageLabelerOptions.Builder().setConfidenceThreshold(0.5f).build()
            )
            val map = HashMap<Long, Map<String, Float>>()
            photos.forEachIndexed { i, p ->
                map[p.id] = runCatching {
                    val bmp = ctx.contentResolver.loadThumbnail(p.uri, Size(320, 320), null)
                    labeler.process(InputImage.fromBitmap(bmp, 0)).await()
                        .associate { it.text to it.confidence }
                }.getOrDefault(emptyMap())
                if (i % 5 == 0) _state.update { it.copy(progress = i + 1) }
            }
            labeler.close()
            vectors = map
            regroup()
            _state.update { it.copy(busy = false, progress = photos.size) }
        }
    }

    fun setThreshold(t: Float) {
        _state.update { it.copy(threshold = t) }
        if (vectors.isNotEmpty() && !_state.value.busy) {
            viewModelScope.launch(Dispatchers.Default) { regroup() }
        }
    }

    private fun regroup() {
        val groups = Clusterer.cluster(photos, vectors, _state.value.threshold)
        _state.update { it.copy(groups = groups) }
    }

    fun saveFolders() {
        if (_state.value.busy) return
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            _state.update { it.copy(busy = true, message = "") }
            var ok = 0
            var fail = 0
            for (g in _state.value.groups) for (p in g.photos) {
                runCatching { PhotoRepo.copyTo(resolver, p, g.folder) }
                    .onSuccess { ok++ }.onFailure { fail++ }
            }
            _state.update {
                it.copy(
                    busy = false,
                    message = "تم نسخ $ok صورة إلى Pictures/PhotoGroups" +
                        if (fail > 0) " (فشل $fail)" else ""
                )
            }
        }
    }
}
