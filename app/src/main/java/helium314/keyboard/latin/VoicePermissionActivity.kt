// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast

/** A keyboard can't ask for a permission itself, so this invisible screen asks for the microphone once. */
class VoicePermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) finish()
        else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 0)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        Toast.makeText(this, if (granted) R.string.voice_input_permission_granted
            else R.string.voice_input_permission_denied, Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        fun launch(context: Context) = context.startActivity(
            Intent(context, VoicePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
