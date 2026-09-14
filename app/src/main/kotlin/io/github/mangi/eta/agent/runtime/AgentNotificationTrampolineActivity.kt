package io.github.mangi.eta.agent.runtime

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.github.mangi.eta.agent.voice.EtaAssistantOverlayService
import io.github.mangi.eta.ui.MainActivity

class AgentNotificationTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (EtaAssistantOverlayService.isServiceActive()) {
                EtaAssistantOverlayService.show(this)
            } else {
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
            }
        } finally {
            finish()
        }
    }
}
