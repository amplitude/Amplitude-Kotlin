package com.amplitude.android.streaming.sample

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.ui.PlayerView

class XmlPlayerActivity : AppCompatActivity() {
    private val viewModel: XmlPlayerViewModel by injectedViewModel { xmlPlayerViewModel }

    private lateinit var playerView: PlayerView
    private lateinit var mediaTitle: TextView
    private lateinit var controls: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_xml_player)

        playerView = findViewById(R.id.player_view)
        mediaTitle = findViewById(R.id.media_title)
        controls = findViewById(R.id.controls)
        playerView.player = viewModel.player.exoPlayer
        bindTitle()

        findViewById<Button>(R.id.play).setOnClickListener { viewModel.player.play() }
        findViewById<Button>(R.id.pause).setOnClickListener { viewModel.player.pause() }
        findViewById<Button>(R.id.seek_back).setOnClickListener { viewModel.player.seekBy(-10_000L) }
        findViewById<Button>(R.id.seek_forward).setOnClickListener { viewModel.player.seekBy(10_000L) }
        findViewById<Button>(R.id.swap_media).setOnClickListener {
            viewModel.player.swapMedia()
            bindTitle()
        }
        findViewById<Button>(R.id.enter_pip).setOnClickListener { enterPipIfPossible() }
    }

    override fun onStop() {
        super.onStop()
        viewModel.onHostStopped(isInPictureInPictureMode, isFinishing)
    }

    override fun onDestroy() {
        super.onDestroy()
        playerView.player = null
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val visible = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
        controls.visibility = visible
        findViewById<View>(R.id.swap_media).visibility = visible
        findViewById<View>(R.id.enter_pip).visibility = visible
        mediaTitle.visibility = visible
        playerView.useController = !isInPictureInPictureMode
    }

    private fun bindTitle() {
        val item = viewModel.player.currentItem
        mediaTitle.text = getString(R.string.video_one_label) + " · " + item.title
    }
}
