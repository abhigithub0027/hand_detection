package com.example.fingerdetector

import android.os.Bundle
import com.example.fingerdetector.base.BaseActivity
import com.example.fingerdetector.ui.home.HomeFragment

class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            showRoot(HomeFragment())
        }
    }
}
 