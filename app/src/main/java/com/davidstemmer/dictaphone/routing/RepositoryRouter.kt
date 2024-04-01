package com.davidstemmer.dictaphone.routing

import com.davidstemmer.dictaphone.media.MediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.davidstemmer.dictaphone.routing.Switchboard.Action
import com.davidstemmer.dictaphone.routing.Switchboard.Callback

class RepositoryRouter(private val repository: MediaRepository): EffectRouter {
    override fun CoroutineScope.handle(state: Switchboard.State, action: Action, dispatch: (Action) -> Unit) {
        when (action) {
            is Action.FetchRecordingData -> launch(Dispatchers.IO) {
                val metadataList = repository.fetchRecordingMetadata()
                dispatch(Callback.Repository.DataFetchComplete(metadataList))
            }
            is Action.UpdateFilename -> launch(Dispatchers.IO) {
                if (action.name.isNotBlank()) {
                    repository.updateName(action.uri, action.name)
                }
                dispatch(Action.FetchRecordingData)
            }
            else -> {}
        }

    }
}

