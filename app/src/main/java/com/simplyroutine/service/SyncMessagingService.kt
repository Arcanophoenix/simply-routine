package com.simplyroutine.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SyncMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        // The Firestore real-time listener handles the actual data sync.
        // FCM wakes the app in the background so the listener can reconnect and fire.
    }

    override fun onNewToken(token: String) {
        // Token refresh is handled the next time the user opens the task list
        // and the ViewModel re-registers with their household.
    }
}
