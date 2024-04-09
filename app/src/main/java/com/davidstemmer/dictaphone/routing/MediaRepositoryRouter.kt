package com.davidstemmer.dictaphone.routing

import com.davidstemmer.dictaphone.media.SimpleMediaRepository
import com.davidstemmer.dictaphone.switchboard.EffectRouter
import com.davidstemmer.dictaphone.switchboard.FilesChanged
import com.davidstemmer.dictaphone.switchboard.MediaRepository
import com.davidstemmer.dictaphone.switchboard.RouterScope
import com.davidstemmer.dictaphone.switchboard.Switchboard
import com.davidstemmer.dictaphone.switchboard.SwitchboardMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MediaRepositoryRouter(private val repository: SimpleMediaRepository):
    EffectRouter<MediaRepository> {

    override fun canHandle(message: SwitchboardMessage) =
        message is MediaRepository

    override fun RouterScope.handle(state: Switchboard.State, action: MediaRepository) {
        when (action) {
            is MediaRepository.ScanForMediaFiles -> launch(Dispatchers.IO) {
                val fileList = repository.fetchRecordingMetadata()
                dispatch(MediaRepository.FileScanComplete(fileList))
                output(FilesChanged(fileList))
            }
            is MediaRepository.UpdateFilename -> launch(Dispatchers.IO) {
                if (action.name.isNotBlank()) {
                    repository.updateName(action.uri, action.name)
                }
                dispatch(MediaRepository.ScanForMediaFiles)
            }
            is MediaRepository.FileScanComplete -> {}
        }
    }
}

