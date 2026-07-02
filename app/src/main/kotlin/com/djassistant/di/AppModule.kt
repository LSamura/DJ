package com.djassistant.di

import com.djassistant.data.log.UnknownCommandLogger
import com.djassistant.data.log.impl.CsvUnknownCommandLogger
import com.djassistant.feature.command.CommandRegistry
import com.djassistant.feature.command.impl.ArtistCommand
import com.djassistant.feature.command.impl.IsPlayingCommand
import com.djassistant.feature.command.impl.NextTrackCommand
import com.djassistant.feature.command.impl.NowPlayingCommand
import com.djassistant.feature.command.impl.PauseCommand
import com.djassistant.feature.command.impl.PlayCommand
import com.djassistant.feature.command.impl.PreviousTrackCommand
import com.djassistant.feature.command.impl.SetVolumeMaxCommand
import com.djassistant.feature.command.impl.SetVolumeMinCommand
import com.djassistant.feature.command.impl.SetVolumePercentCommand
import com.djassistant.feature.command.impl.VolumeDownCommand
import com.djassistant.feature.command.impl.VolumeQueryCommand
import com.djassistant.feature.command.impl.VolumeUpCommand
import com.djassistant.feature.feedback.FeedbackManager
import com.djassistant.feature.feedback.impl.BeepFeedbackManager
import com.djassistant.feature.intent.IntentRecognizer
import com.djassistant.feature.intent.impl.KeywordIntentRecognizer
import com.djassistant.feature.media.MediaRemote
import com.djassistant.feature.media.MediaStateProvider
import com.djassistant.feature.media.impl.SessionMediaRemote
import com.djassistant.feature.settings.SettingsRepository
import com.djassistant.feature.settings.impl.DataStoreSettingsRepository
import com.djassistant.feature.voice.AudioRecorder
import com.djassistant.feature.voice.SpeechRecognizer
import com.djassistant.feature.voice.WakeWordEngine
import com.djassistant.feature.voice.impl.AndroidAudioRecorder
import com.djassistant.feature.voice.impl.PorcupineWakeWordEngine
import com.djassistant.feature.voice.impl.VoskSpeechRecognizer
import com.djassistant.service.DjServiceController
import com.djassistant.service.ServiceController
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds @Singleton
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds @Singleton
    abstract fun bindMediaRemote(impl: SessionMediaRemote): MediaRemote

    @Binds @Singleton
    abstract fun bindMediaStateProvider(impl: SessionMediaRemote): MediaStateProvider

    @Binds @Singleton
    abstract fun bindFeedbackManager(impl: BeepFeedbackManager): FeedbackManager

    @Binds @Singleton
    abstract fun bindIntentRecognizer(impl: KeywordIntentRecognizer): IntentRecognizer

    @Binds @Singleton
    abstract fun bindAudioRecorder(impl: AndroidAudioRecorder): AudioRecorder

    @Binds @Singleton
    abstract fun bindSpeechRecognizer(impl: VoskSpeechRecognizer): SpeechRecognizer

    @Binds @Singleton
    abstract fun bindWakeWordEngine(impl: PorcupineWakeWordEngine): WakeWordEngine

    @Binds @Singleton
    abstract fun bindUnknownCommandLogger(impl: CsvUnknownCommandLogger): UnknownCommandLogger

    @Binds @Singleton
    abstract fun bindServiceController(impl: DjServiceController): ServiceController

    companion object {

        @Provides
        @Singleton
        fun provideCommandRegistry(): CommandRegistry =
            CommandRegistry().apply {
                register(PauseCommand())
                register(PlayCommand())
                register(NextTrackCommand())
                register(PreviousTrackCommand())
                register(VolumeUpCommand())
                register(VolumeDownCommand())
                register(SetVolumeMaxCommand())
                register(SetVolumeMinCommand())
                register(SetVolumePercentCommand())
                register(NowPlayingCommand())
                register(ArtistCommand())
                register(IsPlayingCommand())
                register(VolumeQueryCommand())
            }
    }
}
