package com.pixelplay.dotsboxes

import android.app.Application
import com.pixelplay.dotsboxes.data.local.GameDataStore
import com.pixelplay.dotsboxes.data.repository.GameRepositoryImpl
import com.pixelplay.dotsboxes.domain.repository.GameRepository
import com.pixelplay.dotsboxes.sound.SoundManager

class DotsBoxesApp : Application() {

    // Manual DI — created once, shared app-wide
    val gameDataStore by lazy { GameDataStore(this) }
    val gameRepository: GameRepository by lazy { GameRepositoryImpl(gameDataStore) }
    val soundManager by lazy { SoundManager(this) }
}
