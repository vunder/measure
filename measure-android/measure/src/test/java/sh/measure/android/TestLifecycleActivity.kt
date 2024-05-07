package sh.measure.android

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import sh.measure.android.lifecycle.TestFragment

internal class TestLifecycleActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.beginTransaction().add(TestFragment(), "test-fragment").commit()
    }
}
